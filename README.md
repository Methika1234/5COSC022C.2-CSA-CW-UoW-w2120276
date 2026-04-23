# Smart Campus Sensor & Room Management API

A JAX-RS RESTful service developed for the University of Westminster "Smart Campus" initiative. The API manages three resources — rooms, sensors, and sensor readings — and enforces the referential integrity and state-based rules described in the coursework specification.

## Module context

| Field | Value |
|-------|-------|
| Module | 5COSC022W, Client Server Architectures |
| Student ID | w2120276 |

This README serves two purposes. The top half is the GitHub-facing overview with build instructions and sample curl commands. Section 7 is the Conceptual Report required by the specification, which states: "the report must be organised and written in the README.md file on GitHub. The report must only include the answers to the questions in each part."

## API overview

The service exposes four resource groups under the versioned base path `/api/v1`:

| Resource | URI prefix | Implementation |
|----------|-----------|----------------|
| Discovery | `/api/v1` | `DiscoveryResource` |
| Rooms | `/api/v1/rooms` | `SensorRoom` |
| Sensors | `/api/v1/sensors` | `SensorResource` |
| Readings (sub resource) | `/api/v1/sensors/{sensorId}/readings` | `SensorReadingResource` |

Design choices worth flagging up front:

- **In-memory storage only.** The specification forbids a database. Each repository (`repository/RoomRepository.java`, `repository/SensorRepository.java`) holds a `private static final List<T>` wrapped in `Collections.synchronizedList`, guarded by `synchronized` blocks on every read and write. `repository/ReadingRepository.java` keys a plain `HashMap` by `sensorId` and guards it with `synchronized (readingHistory)` blocks, so all three repositories share the same locking idiom.

- **Three-tier separation.** Resources (`resource/*.java`) do nothing but JAX-RS mapping and `Response` construction. Business rules and multi-repository orchestration live in the service layer (`service/RoomService.java`, `service/SensorService.java`, `service/SensorReadingService.java`). The service layer is where the three domain rules the specification lists are enforced: a sensor cannot be registered for a non-existent room, a room cannot be deleted while any sensor still references it, and a sensor under maintenance refuses new readings. Each violation maps to a distinct HTTP status code — 422, 409, 403 respectively.

- **JSON everywhere.** Every resource method either produces or consumes `application/json`. Each of the three domain exception mappers returns a structured `ErrorMessage` body (`errorMessage` + `errorCode`) rather than the servlet container's default error page.

- **Stack traces never leave the process.** `GlobalExceptionHandler` is the global safety net. `WebApplicationException` subtypes (400 / 404 / 405 / 406 / 415 raised by Jersey's content negotiation and dispatch machinery) pass through with their intended status, and every other `Throwable` is logged server-side through `java.util.logging` and returned as a generic 500 body. Every error response, mapped or inline 404, uses the uniform `{errorMessage, errorCode}` envelope.

## Build and run

### Prerequisites

- JDK 11 or later
- Maven 3.6+
- A Servlet 3.1+ container. The project was developed against GlassFish 5 (the container NetBeans ships by default). Tomcat 9 and Payara 5 also work because Jersey 2.34 targets the `javax.ws.rs` namespace.

### Build

```bash
cd w2120276
mvn clean package
# produces: target/w2120276.war
```

### Deploy

Either open the project in NetBeans and choose **Run Project** (which deploys to the bundled GlassFish), or copy the `.war` into the container's `webapps/` directory. The default context root matches the artefact name, so the full base URL is:

```
http://localhost:8080/w2120276/api/v1
```

A verification smoke test after deployment:

```bash
curl -s http://localhost:8080/w2120276/api/v1
```

A 200 response containing the resources map confirms that the JAX-RS application has bootstrapped and classpath scanning has picked up the resource classes.

### Fresh start behaviour

The three in-memory stores begin empty on every deployment. The curl sequence below is ordered as a self-contained demonstration: steps 2 to 5 create the rooms and sensors that the later steps read, filter, and operate on.

## Sample curl commands

The specification requires at least five. The twelve calls below cover discovery, happy-path CRUD, filtering, the sub-resource locator, and each of the three domain-specific error paths. They are designed to run end to end on a freshly deployed WAR.

```bash
BASE="http://localhost:8080/w2120276/api/v1"
```

### 1. Discovery — API metadata and resource map (HATEOAS entry point)

```bash
curl -s "$BASE"
```

### 2. Create a room (expect 201 Created + Location header)

```bash
curl -s -i -X POST "$BASE/rooms" \
     -H "Content-Type: application/json" \
     -d '{"id":"LIB-301","name":"Library Quiet Study","capacity":40}'
```

### 3. Create a second room

```bash
curl -s -i -X POST "$BASE/rooms" \
     -H "Content-Type: application/json" \
     -d '{"id":"LEC-101","name":"Lecture Hall A","capacity":120}'
```

### 4. Register a sensor inside the first room

```bash
curl -s -i -X POST "$BASE/sensors" \
     -H "Content-Type: application/json" \
     -d '{"id":"TEMP-001","type":"Temperature","status":"ACTIVE","currentValue":21.3,"roomId":"LIB-301"}'
```

### 5. Register a sensor that is already under MAINTENANCE (for the 403 path below)

```bash
curl -s -i -X POST "$BASE/sensors" \
     -H "Content-Type: application/json" \
     -d '{"id":"OCC-001","type":"Occupancy","status":"MAINTENANCE","currentValue":0.0,"roomId":"LEC-101"}'
```

### 6. List all rooms

```bash
curl -s "$BASE/rooms"
```

### 7. Fetch a single room by id

```bash
curl -s "$BASE/rooms/LIB-301"
```

### 8. Filter sensors by type with a query parameter

```bash
curl -s "$BASE/sensors?type=Temperature"
```

### 9. Append a reading to a sensor (side effect: parent currentValue updates)

```bash
curl -s -i -X POST "$BASE/sensors/TEMP-001/readings" \
     -H "Content-Type: application/json" \
     -d '{"value":22.8,"timestamp":1713352800000}'

# Re-read the readings history to confirm the reading was recorded
curl -s "$BASE/sensors/TEMP-001/readings"
```

### 10. Error path — delete a room that still has sensors (expect 409 Conflict)

```bash
curl -s -i -X DELETE "$BASE/rooms/LIB-301"
```

### 11. Error path — register a sensor for a non-existent room (expect 422 Unprocessable Entity)

```bash
curl -s -i -X POST "$BASE/sensors" \
     -H "Content-Type: application/json" \
     -d '{"id":"HEAT-999","type":"Temperature","status":"ACTIVE","currentValue":19.0,"roomId":"NOT-A-ROOM"}'
```

### 12. Error path — post a reading to a sensor in MAINTENANCE (expect 403 Forbidden)

```bash
curl -s -i -X POST "$BASE/sensors/OCC-001/readings" \
     -H "Content-Type: application/json" \
     -d '{"value":5.0}'
```

## Project structure

```
w2120276/
├── pom.xml                                     Maven build, packaging = war
└── src/main/
    ├── java/com/mycompany/new_cw/
    │   ├── JakartaRestConfiguration.java       @ApplicationPath("/api/v1")
    │   ├── resource/
    │   │   ├── DiscoveryResource.java          GET /api/v1
    │   │   ├── SensorRoom.java                 /api/v1/rooms
    │   │   ├── SensorResource.java             /api/v1/sensors
    │   │   └── SensorReadingResource.java      sub-resource, constructed by locator
    │   ├── service/
    │   │   ├── RoomService.java                orphan-room check (409)
    │   │   ├── SensorService.java              roomId referential integrity (422)
    │   │   └── SensorReadingService.java       MAINTENANCE guard (403) + currentValue side-effect
    │   ├── repository/
    │   │   ├── RoomRepository.java             static synchronized list + INTEGRITY_LOCK monitor
    │   │   ├── SensorRepository.java           static synchronized list
    │   │   └── ReadingRepository.java          HashMap<sensorId, List> under synchronized(readingHistory)
    │   ├── model/
    │   │   ├── ErrorMessage.java               JSON error envelope (errorMessage + errorCode)
    │   │   ├── Room.java
    │   │   ├── Sensor.java
    │   │   └── SensorReading.java
    │   ├── exception/
    │   │   ├── RoomNotEmptyException.java      custom exception
    │   │   ├── RoomConflictMapper.java         409 mapper
    │   │   ├── LinkedResourceNotFoundException.java  custom exception
    │   │   ├── InvalidReferenceMapper.java     422 mapper
    │   │   ├── SensorUnavailableException.java custom exception
    │   │   ├── MaintenanceSensorMapper.java    403 mapper
    │   │   └── GlobalExceptionHandler.java     500 catch-all
    │   └── filter/
    │       └── RequestResponseLoggingFilter.java  request/response logging
    └── webapp/
        ├── index.html                          Landing page
        ├── META-INF/context.xml
        └── WEB-INF/
            ├── beans.xml
            └── web.xml
```

404 responses for missing rooms or sensors are thrown as `NotFoundException` from the resource and service methods, which `GlobalExceptionHandler` intercepts and renders with the preserved 404 status. The three mapper-backed codes (409, 422, 403) plus the 500 catch-all are the exact set the specification Part 5 enumerates.

## 7. Conceptual Report

The questions below are transcribed verbatim from the coursework specification. Each answer cites the file in which the relevant implementation lives. File paths are relative to the project root (`w2120276/`).

### Question 1 — JAX-RS resource lifecycle and in-memory thread safety

**Question.** In your report, explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions.

By default, Jersey creates a new instance of each resource class for every request that comes in. The JAX-RS spec refers to this as the "per-request" lifecycle. None of the resource classes in this project use `@Singleton`, and there is no custom binding in `JakartaRestConfiguration.java`, so they all follow this default. This means any instance fields on a resource class are only visible to the one thread handling that request — `SensorRoom`, `SensorResource`, and `SensorReadingResource` each store a service-layer reference as an instance field, and it never leaks to another thread.

The shared data that actually lives across requests is in the repository layer. `repository/RoomRepository.java` and `repository/SensorRepository.java` both declare a `private static final List<T>` wrapped in `Collections.synchronizedList(new ArrayList<>())`, and every method that touches the list does so inside a `synchronized (roomStore)` or `synchronized (sensorStore)` block. This prevents two things: a `ConcurrentModificationException` if one thread is iterating the list while another removes from it, and lost updates when two threads write at the same time. The readings store in `repository/ReadingRepository.java` works the same way — it has a `static final HashMap<String, List<SensorReading>>` keyed by `sensorId`, and every access goes through `synchronized (readingHistory)`. The lock also covers the lazy-create path (when the first reading arrives for a new sensor), so two threads posting simultaneously end up with the same list rather than overwriting each other.

The tricky part is the rule "a room can only be deleted if no sensors reference it". This is a check-then-act across two stores, and locking each store individually is not enough. Without a shared lock, two race conditions can break the invariant. First, `RoomService.decommissionRoom` could check for sensors, find none, and start deleting the room — meanwhile `SensorService.registerSensor` is in the middle of adding a sensor to that same room. The sensor ends up pointing at a deleted room. Second, the reverse: `registerSensor` confirms the room exists, but before it finishes inserting, `decommissionRoom` runs, sees no sensors, and deletes the room. Either way, you get an orphaned sensor.

To fix this, `repository/RoomRepository.java` declares a `public static final Object INTEGRITY_LOCK`. Both `service/RoomService.decommissionRoom` and `service/SensorService.registerSensor` grab this lock before doing anything. The decommission logic looks like this, and `registerSensor` uses the same `synchronized (RoomRepository.INTEGRITY_LOCK)` wrapper around its room lookup, sensor insert, and `sensorIds` update.

```java
public void decommissionRoom(String id) {
    synchronized (RoomRepository.INTEGRITY_LOCK) {
        Room existing = roomRepo.findById(id);
        if (existing == null) {
            throw new NotFoundException("No room exists with the identifier '" + id + "'.");
        }
        if (!sensorRepo.findByRoomId(id).isEmpty()) {
            int count = sensorRepo.findByRoomId(id).size();
            throw new RoomNotEmptyException(
                    "Cannot decommission room '" + id + "' while " + count
                    + " sensor(s) remain deployed. Relocate or unregister all sensors first.");
        }
        roomRepo.remove(id);
    }
}
```

Only these two operations need the shared lock. Normal reads are still concurrent, and posting sensor readings uses a separate lock on the readings store, so that path is not affected.

### Question 2 — Hypermedia and HATEOAS

**Question.** Why is the provision of "Hypermedia" (links and navigation within responses) considered a core feature of advanced REST design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

HATEOAS stands for Hypermedia As The Engine Of Application State. The idea is that API responses include links telling the client what it can do next, rather than the client having to know all the URLs upfront from documentation. This matters because it decouples the client from the server's URL structure. If the server renames an endpoint or bumps to a new version, a client that follows links from the response will still work — it never had the old URL hardcoded in the first place.

In this API, `resource/DiscoveryResource.java` serves as the entry point. `GET /api/v1` returns a JSON object with admin details, a version number, and a `resources` map listing each top-level collection with its full URL:

```json
{
  "apiName": "Smart Campus Sensor & Room Management API",
  "version": "1.0.0",
  "administrator": {
    "name": "Methika Fernando",
    "email": "methika.fernando@university.edu"
  },
  "resources": {
    "rooms": "http://localhost:8080/w2120276/api/v1/rooms",
    "sensors": "http://localhost:8080/w2120276/api/v1/sensors"
  }
}
```

These URIs are built at runtime using `UriInfo.getBaseUriBuilder()`, so they always match the actual deployment — nothing is hardcoded. A client only needs to bookmark the discovery URL and follow the links from there.

Taking this further, a fully HATEOAS-compliant API would also embed links in individual resource responses. For example, a room response could include a `_links` block showing what the client can do next:

```json
{
  "id": "LIB-301",
  "name": "Library Quiet Study",
  "capacity": 40,
  "sensorIds": ["TEMP-001"],
  "_links": {
    "self": "http://localhost:8080/w2120276/api/v1/rooms/LIB-301",
    "sensors": "http://localhost:8080/w2120276/api/v1/sensors?roomId=LIB-301",
    "collection": "http://localhost:8080/w2120276/api/v1/rooms"
  }
}
```

This API does not include per-resource links since the spec only asks for the discovery endpoint, but the example shows the idea — a client following `_links.sensors` instead of building the URL itself will keep working even if the server changes its path layout.

The advantage over static documentation is straightforward. A PDF or README with sample URLs is a copy of the server's URL scheme, and it goes stale the moment someone changes the code. A discovery response comes from the same code that handles requests, so it cannot be out of date. On top of that, hypermedia lets the server show or hide links based on state. For instance, a sensor in `ACTIVE` state could include a "post reading" link, while one in `MAINTENANCE` would leave it out — a client could disable the button without making an extra request. Similarly, a room with sensors attached could show a `sensors` link but hide the `delete` link, reflecting the 409 business rule.

### Question 3 — Returning IDs versus full objects in list endpoints

**Question.** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client-side processing.

It comes down to a trade-off: one bigger response with everything in it, or a small list of IDs that forces the client to make follow-up requests for the details.

**Option 1: ID-only collection**

An ID-only response is small:

```json
["LIB-301", "LEC-101", "LAB-201"]
```

For a campus with thousands of rooms, this is much smaller than sending full objects — good for mobile clients or dashboards that only need a drop-down. But the downside is the N+1 problem: if the UI then wants to show each room's name or capacity, it has to make a separate `GET /rooms/{id}` call for every single ID. Once the list grows, those extra requests cost more than the bandwidth saved on the first call.

**Option 2: Full object collection**

A full-object response of the same collection looks like this:

```json
[
  {
    "id": "LIB-301",
    "name": "Library Quiet Study",
    "capacity": 40,
    "sensorIds": ["TEMP-001"]
  },
  {
    "id": "LEC-101",
    "name": "Lecture Hall A",
    "capacity": 120,
    "sensorIds": []
  }
]
```

This is what `SensorRoom.listAllRooms()` in `resource/SensorRoom.java` does. The response is bigger, but it removes the N+1 problem entirely. It makes sense here because a `Room` object is small — just an ID, a name, a capacity integer, and a short list of sensor IDs. The campus has a bounded number of rooms, so the payload stays predictable.

One thing worth noting is that `Room.sensorIds` only holds sensor IDs, not full `Sensor` objects. If the client needs full sensor details, it goes to `/sensors` or uses the filter at `/sensors?type=...`. This avoids duplicating sensor data inside every room response and keeps each resource independent. The API does not implement pagination or field selection since the spec does not require it and the dataset is small enough that it is not needed.

### Question 4 — DELETE idempotency

**Question.** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

Yes. The key thing to understand is that idempotency is about what happens to the server's state, not what status code the client gets back. An operation is idempotent if running it once or running it ten times leaves the server in the same state. The response code can change between calls — that is fine.

Walking through it with `SensorRoom.removeRoom` in `resource/SensorRoom.java`, which calls into `service/RoomService.java`:

1. **First call (room exists, no sensors attached).** `roomService.decommissionRoom(roomId)` finds the room, confirms no sensors reference it via `sensorRepo.findByRoomId(id)`, removes it, and returns. The resource sends back **204 No Content**. After this call, the room is gone.

2. **Second identical call.** `roomService.decommissionRoom(roomId)` tries to look up the room, `roomRepo.findById(id)` returns `null`, and the service throws `NotFoundException`. `GlobalExceptionHandler` catches it and returns **404 Not Found** with an `ErrorMessage` body. The server state has not changed — the room is still gone.

3. **Third, fourth, fifth call...** Same as call 2. The store is untouched each time.

The status code changes from 204 to 404 after the first call, but the server state is the same: the room does not exist. That is what idempotency requires. A client that retries because it lost the first response will end up in the same place as one that got the 204 on the first try.

The 409 Conflict case (room still has sensors) does not break this either. A 409 leaves the room exactly as it was, so repeating the request does not change anything. Once the sensors are removed by a different request, the next DELETE succeeds and after that it falls into the 404 pattern above.

### Question 5 — @Consumes mismatch consequences

**Question.** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

`@Consumes` tells Jersey what content types a method can accept. When a request comes in, Jersey checks the `Content-Type` header against the `@Consumes` values on the matching resource methods. If nothing matches, Jersey does not even create the resource object or call the method — it throws `javax.ws.rs.NotSupportedException`, which results in **HTTP 415 Unsupported Media Type**.

In this project, the 415 goes through `exception/GlobalExceptionHandler`, which keeps the status code from the `WebApplicationException` and wraps it in the standard JSON error format:

```json
{
  "errorMessage": "HTTP 415 Unsupported Media Type",
  "errorCode": 415
}
```

This has two practical effects. First, the business logic in `service/SensorService.registerSensor` — like the `roomRepo.findById` check that throws `LinkedResourceNotFoundException` — only runs when the request body is actually JSON. A client sending `application/xml` or `text/plain` gets rejected before any application code executes, so there is no risk of a non-JSON body being half-parsed. Second, a 415 is a clear signal to the client: resending the same payload will not work, you need to send JSON. This is different from a 500, which a client might retry thinking it was a temporary server issue.

`@Produces` works in the opposite direction — it matches against the `Accept` header, and a mismatch gives **HTTP 406 Not Acceptable**. Together, `@Consumes(APPLICATION_JSON) @Produces(APPLICATION_JSON)` means "I take JSON in and send JSON back". Without `@Consumes`, the method would accept any content type, and Jackson would try to parse whatever came in — which would likely fail in confusing ways. By declaring it explicitly on every POST method in `SensorRoom`, `SensorResource`, and `SensorReadingResource`, bad content types get a clean 415 before the application code even runs.

### Question 6 — @QueryParam versus @PathParam for filtering

**Question.** You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

In REST, path segments identify a resource and query parameters filter or refine it. `/api/v1/sensors` identifies the sensor collection. `/api/v1/sensors?type=CO2` is still the same collection, just filtered down. The resource has not changed — only what the server returns from it.

Putting the filter in the path like `/api/v1/sensors/type/CO2` creates a fake hierarchy that does not exist in the domain. It makes it look like `type` is a container with `CO2` inside it. And it gets worse when you add more filters: you would need `/sensors/status/ACTIVE`, `/sensors/room/LIB-301`, and then combinations like `/sensors/type/CO2/status/ACTIVE/room/LIB-301`. Each of those paths needs its own route and its own test. With query parameters, you just add them to one method. Here is the handler in `resource/SensorResource.java` — adding a `status` filter would just mean adding another `@QueryParam`:

```java
@GET
@Produces(MediaType.APPLICATION_JSON)
public Response listSensors(@QueryParam("type") String type) {
    List<Sensor> result;
    if (type != null && !type.trim().isEmpty()) {
        result = sensorService.filterByType(type);
    } else {
        result = sensorService.retrieveAll();
    }
    return Response.ok(result).build();
}
```

Query parameters also handle the "no filter" case naturally — just leave the parameter out and you get everything. With a path-based filter, you would need something like `/sensors/type/all` to get the unfiltered list, which is awkward and could clash with an actual sensor type called "all".

The rule of thumb is: path segments for things that are actual resources (a specific sensor by its ID), query parameters for filtering, sorting, or paginating a collection.

### Question 7 — Benefits of the sub-resource locator pattern

**Question.** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., `sensors/{id}/readings/{rid}`) in one massive controller class?

A sub-resource locator is a method that has `@Path` but no HTTP method annotation like `@GET` or `@POST`. Instead of handling the request itself, it returns an object, and that object's methods handle the rest of the URL. The locator in `resource/SensorResource.java` looks like this:

```java
@Path("{sensorId}/readings")
public SensorReadingResource readingsSubResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

When a request comes in for `/sensors/{sensorId}/readings`, Jersey calls this method, gets back a `SensorReadingResource` instance, and lets that instance handle the request from there.

The first benefit is **separation of concerns**. `SensorResource` deals with sensors. `SensorReadingResource` deals with readings for a specific sensor. Neither class has logic from the other's domain. Without this pattern, you end up with one big controller class that has sensor methods and reading methods all mixed together — the kind of 800-line file that is hard to work with.

The second benefit is **scoped parent context**. The locator passes the `sensorId` into the sub-resource constructor: `new SensorReadingResource(sensorId)`. The sub-resource then looks up the parent sensor through `SensorReadingService` when it actually needs it. If the sensor does not exist, the service throws a `NotFoundException` and `GlobalExceptionHandler` returns a 404. Because the sub-resource holds just the ID (not a captured `Sensor` object), it always sees the latest state — if another thread changes the sensor between the locator running and the sub-resource method running, it does not matter. All the business logic (the MAINTENANCE check, UUID generation, updating `currentValue`) lives in `SensorReadingService.recordNewReading`, keeping the resource class thin.

The third benefit is that it **scales well**. If readings needed their own nested resource later (say `/sensors/{id}/readings/{rid}/annotations`), you just add another locator inside `SensorReadingResource`. The parent class does not grow at all. It is also easier to test — `SensorReadingResource` is just a normal Java class with a constructor, so you can test it directly without spinning up Jersey.

### Question 8 — Why 422 beats 404 for a broken reference in a valid payload

**Question.** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

The three status codes that could apply here each mean something different. **404 Not Found** means the URL itself does not point to anything. **400 Bad Request** means the request is malformed — broken JSON, missing fields, wrong data types. **422 Unprocessable Entity** means the request is well-formed and the server understands it, but it cannot process it because a business rule or constraint fails.

Take this payload, sent to `POST /api/v1/sensors`:

```json
{
  "id": "HEAT-999",
  "roomId": "NOT-A-ROOM",
  "type": "Temperature",
  "status": "ACTIVE",
  "currentValue": 19.0
}
```

This falls into the 422 category. The URL `/sensors` exists — the sensors collection is right there — so 404 would be misleading. The JSON is perfectly valid with all the right fields and types, so 400 does not fit either. The problem is that `roomId` references a room that does not exist. The request makes sense syntactically but fails a domain constraint.

In the code, `SensorService.registerSensor` throws `LinkedResourceNotFoundException` when the room lookup fails, and `InvalidReferenceMapper` turns that into a 422 with a JSON `ErrorMessage` body. This tells the client three things: the request was understood, resending it will not help, and the fix is to either create that room first or use a correct `roomId`. A 404 would make the client think the `/sensors` endpoint is gone. A 400 would make them look for a typo or missing field that is not there. 422 communicates the actual problem.

### Question 9 — Cybersecurity risks of leaked stack traces

**Question.** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

A raw stack trace gives an attacker a lot of useful information for free. There are four main things they can extract:

**Package structure and internal paths.** A stack frame like `com.mycompany.new_cw.service.RoomService.decommissionRoom` immediately reveals the root package, the three-layer architecture (`resource`, `service`, `repository`), and the exact line that failed. The attacker now has a map of the codebase without needing to decompile anything.

**Library names and versions.** Stack traces include frames from third-party code — things like `org.glassfish.jersey.server.ServerRuntime$1.run` or `com.fasterxml.jackson.databind.ObjectMapper.readValue`. These tell the attacker exactly which libraries and often which versions are in use. From there, they can search for known vulnerabilities in those specific versions.

**Logic flaws.** A `NullPointerException` at `SensorRoom.removeRoom:30` tells the attacker that a specific code path has a null dereference bug. They can then craft requests to hit that branch on purpose — either to crash the service repeatedly or to look for deeper issues.

**Server configuration.** Stack traces sometimes include file system paths, OS usernames, or container details. None of this is something an API consumer needs, and all of it helps an attacker narrow down the attack surface.

This project prevents all of this with `exception/GlobalExceptionHandler.java`. It implements `ExceptionMapper<Throwable>` as a catch-all. `WebApplicationException` subtypes (400, 404, 405, 406, 415) pass through with their normal status codes. Everything else gets logged server-side with `java.util.logging` and the client receives a generic 500 with the message "An unexpected server-side error has occurred. The incident has been logged for investigation." No package names, no library versions, no line numbers — nothing internal leaves the server.
