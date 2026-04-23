# Smart Campus Sensor & Room Management API

A JAX-RS REST API built for the University of Westminster "Smart Campus" project. It manages rooms, sensors, and sensor readings, and enforces the business rules from the coursework spec (referential integrity between rooms and sensors, state-based restrictions on readings).

## Module context

| Field | Value |
|-------|-------|
| Module | 5COSC022W, Client Server Architectures |
| Student ID | w2120276 |

The first half of this README covers build instructions and sample curl commands. Section 7 is the Conceptual Report — the spec says it should go in the README on GitHub and only contain answers to the given questions.

## API overview

The service exposes four resource groups under the versioned base path `/api/v1`:

| Resource | URI prefix | Implementation |
|----------|-----------|----------------|
| Discovery | `/api/v1` | `DiscoveryResource` |
| Rooms | `/api/v1/rooms` | `SensorRoom` |
| Sensors | `/api/v1/sensors` | `SensorResource` |
| Readings (sub resource) | `/api/v1/sensors/{sensorId}/readings` | `SensorReadingResource` |

A few design choices to note:

- **In-memory storage only.** No database — the spec does not allow one. `repository/RoomRepository.java` and `repository/SensorRepository.java` use a `private static final List<T>` wrapped in `Collections.synchronizedList`, with `synchronized` blocks on every read/write. `repository/ReadingRepository.java` uses a `HashMap` keyed by `sensorId`, also guarded by `synchronized` blocks.

- **Three-tier split.** Resource classes (`resource/*.java`) only handle JAX-RS routing and build `Response` objects. Business rules live in the service layer (`service/RoomService.java`, `service/SensorService.java`, `service/SensorReadingService.java`). The three main rules are: you cannot register a sensor for a room that does not exist (422), you cannot delete a room that still has sensors (409), and you cannot post readings to a sensor in MAINTENANCE (403).

- **JSON everywhere.** All resource methods produce and consume `application/json`. Error responses use a consistent `ErrorMessage` JSON body with `errorMessage` and `errorCode` fields, not the default server error page.

- **No stack traces exposed.** `GlobalExceptionHandler` catches everything. Known `WebApplicationException` types (400, 404, 405, 406, 415) keep their status code. Anything else gets logged with `java.util.logging` and the client gets a generic 500. All errors use the same `{errorMessage, errorCode}` format.

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

Open the project in NetBeans and hit **Run Project** (deploys to GlassFish), or drop the `.war` into the container's `webapps/` folder. The base URL is:

```
http://localhost:8080/w2120276/api/v1
```

Quick check that it is running:

```bash
curl -s http://localhost:8080/w2120276/api/v1
```

If you get a 200 with the resources map, the API is up.

### Fresh start behaviour

Everything starts empty on each deployment. The curl commands below are ordered so they work on a fresh deploy — steps 2-5 create the data that the rest of the commands use.

## Sample curl commands

The spec asks for at least five. These twelve cover discovery, basic CRUD, filtering, the sub-resource, and all three error paths. Run them in order on a fresh deploy.

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

Missing rooms or sensors throw `NotFoundException`, which `GlobalExceptionHandler` catches and returns as a 404. The three custom mappers (409, 422, 403) plus the 500 catch-all cover what the spec asks for in Part 5.

## 7. Conceptual Report

Questions are copied from the coursework spec. File paths below are relative to `w2120276/`.

---

### Part 1: Service Architecture & Setup

#### 1.1 — Project & Application Configuration

**Question.** In your report, explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions.

Jersey creates a new resource class instance for every request — this is the "per-request" lifecycle, and it is the default in JAX-RS. I have not annotated any resource class with `@Singleton` or set up any custom binding in `JakartaRestConfiguration.java`, so all of them (`SensorRoom`, `SensorResource`, `SensorReadingResource`) follow this default. Because each instance only exists for one request, its instance fields (like the service-layer references) are only seen by the thread handling that request.

The data that needs to survive across requests lives in the repositories. `repository/RoomRepository.java` and `repository/SensorRepository.java` each have a `private static final List<T>` wrapped in `Collections.synchronizedList(new ArrayList<>())`. Every method that reads or writes the list does so inside a `synchronized (roomStore)` / `synchronized (sensorStore)` block. This stops `ConcurrentModificationException` (one thread iterating while another removes) and lost updates (two threads writing at the same time). `repository/ReadingRepository.java` uses the same approach — a `static final HashMap<String, List<SensorReading>>` keyed by `sensorId`, with all access inside `synchronized (readingHistory)`. The lock also covers the lazy-create when the first reading arrives for a sensor, so two threads posting at the same time do not each create their own list.

The harder problem is the rule "a room can only be deleted if no sensors point to it". This check spans two stores, and locking them separately is not enough. Without a shared lock, you get race conditions. For example, `RoomService.decommissionRoom` checks for sensors, finds none, and starts removing the room — but at the same time `SensorService.registerSensor` is adding a sensor to that room. Now the sensor points to a deleted room. The reverse can happen too: `registerSensor` confirms the room exists, but `decommissionRoom` deletes it before the insert finishes. Either way you end up with an orphaned sensor.

The fix is a shared lock. `repository/RoomRepository.java` has a `public static final Object INTEGRITY_LOCK`, and both `service/RoomService.decommissionRoom` and `service/SensorService.registerSensor` acquire it before doing anything. Here is the decommission method — `registerSensor` wraps its room lookup, sensor insert, and `sensorIds` update in the same `synchronized (RoomRepository.INTEGRITY_LOCK)` block.

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

Only these two operations need the shared lock. Regular reads still run concurrently, and posting readings locks the readings store separately so it is not blocked by room/sensor operations.

#### 1.2 — The Discovery Endpoint

**Question.** Why is the provision of "Hypermedia" (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

HATEOAS stands for Hypermedia As The Engine Of Application State. Basically, API responses should include links so the client knows what it can do next without having every URL hardcoded. The point is that if the server changes its URL layout or moves to a new version, clients that follow links from responses keep working — they never had the old URLs baked in.

In this API, `resource/DiscoveryResource.java` is the starting point. `GET /api/v1` returns admin info, a version number, and a `resources` map with URLs for each collection:

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

The URLs are built at runtime with `UriInfo.getBaseUriBuilder()`, so they match wherever the app is deployed. A client just needs to know the discovery URL and follow links from there.

Going further, a full HATEOAS implementation would also put links inside individual responses. A room could look like this:

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

I did not add per-resource links since the spec only requires the discovery endpoint, but the example shows what it would look like — a client that follows `_links.sensors` keeps working even if the server rearranges its paths.

Compared to static documentation, the main win is that a discovery response is generated by the running server, so it cannot go out of sync with the actual endpoints. A PDF or a README with sample URLs is just a snapshot that goes stale the moment someone changes the code. Hypermedia also lets the server control what the client sees based on state — for example, a sensor in `ACTIVE` could include a "post reading" link, but one in `MAINTENANCE` would leave it out. A room with sensors could show a `sensors` link but hide `delete`, matching the 409 rule.

---

### Part 2: Room Management

#### 2.1 — Room Resource Implementation

**Question.** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client-side processing.

There are two ways to do it, and each has trade-offs.

**Option 1: ID-only collection**

An ID-only response is small:

```json
["LIB-301", "LEC-101", "LAB-201"]
```

If a campus has thousands of rooms, this is way smaller than sending full objects. Works well for mobile clients or a drop-down that only needs IDs. The problem is that if the UI wants to show names or capacities, it needs a separate `GET /rooms/{id}` for each one. That is the N+1 problem — all those follow-up requests can end up costing more than just sending the full data in one go.

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

This is what `SensorRoom.listAllRooms()` in `resource/SensorRoom.java` does. The response is bigger but there is no N+1. It works here because `Room` objects are small — an ID, a name, a capacity, and a short sensor ID list — and a campus only has so many rooms.

Worth noting: `Room.sensorIds` only has sensor IDs, not full `Sensor` objects. If you need sensor details, you hit `/sensors` or filter with `/sensors?type=...`. This keeps sensor data in one place instead of duplicating it inside every room. I did not add pagination or field selection because the spec does not ask for it and the data is small enough.

#### 2.2 — Room Deletion & Safety Logic

**Question.** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

Yes. Idempotency is about the server state, not the response code. If you send the same request once or ten times and the server ends up in the same state, it is idempotent. The status code can change between calls.

Here is what happens with `SensorRoom.removeRoom` in `resource/SensorRoom.java` (which delegates to `service/RoomService.java`):

1. **First call (room exists, no sensors attached).** `roomService.decommissionRoom(roomId)` finds the room, confirms no sensors reference it via `sensorRepo.findByRoomId(id)`, removes it, and returns. The resource sends back **204 No Content**. After this call, the room is gone.

2. **Second identical call.** `roomService.decommissionRoom(roomId)` tries to look up the room, `roomRepo.findById(id)` returns `null`, and the service throws `NotFoundException`. `GlobalExceptionHandler` catches it and returns **404 Not Found** with an `ErrorMessage` body. The server state has not changed — the room is still gone.

3. **Third, fourth, fifth call...** Same as call 2. The store is untouched each time.

The status code goes from 204 to 404 after the first call, but the server state is the same both times — the room does not exist. That satisfies idempotency. If a client retries because it never got the first response, it still ends up in the right place.

The 409 case (room has sensors) does not break this. A 409 leaves the room untouched, so repeating it changes nothing. Once the sensors get removed, the next DELETE succeeds and then you are back to the 404 pattern.

---

### Part 3: Sensor Operations & Linking

#### 3.1 — Sensor Resource & Integrity

**Question.** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

`@Consumes` tells Jersey which content types the method accepts. Before calling the method, Jersey checks the incoming `Content-Type` header against it. If there is no match, the method never runs — Jersey throws `javax.ws.rs.NotSupportedException` and the client gets **HTTP 415 Unsupported Media Type**.

In this project, `exception/GlobalExceptionHandler` catches the 415, keeps the status code, and wraps it in the standard JSON error body:

```json
{
  "errorMessage": "HTTP 415 Unsupported Media Type",
  "errorCode": 415
}
```

Two things follow from this. First, the business logic in `service/SensorService.registerSensor` (like the room lookup that throws `LinkedResourceNotFoundException`) only runs when the body is actually JSON. If someone sends `application/xml` or `text/plain`, they get rejected before any of my code runs — no risk of half-parsing something weird. Second, 415 clearly tells the client "this will never work with that content type, send JSON instead". A 500 might make them think it is a server bug and retry.

`@Produces` does the same thing in reverse — it checks the `Accept` header, and if the client wants something other than JSON, it gets **406 Not Acceptable**. Together, `@Consumes(APPLICATION_JSON) @Produces(APPLICATION_JSON)` basically says "JSON in, JSON out". Without `@Consumes`, the method would try to accept anything, and Jackson would attempt to deserialize it — which would probably blow up in confusing ways. I declared it on every POST in `SensorRoom`, `SensorResource`, and `SensorReadingResource` so bad content types get a clean 415 instead.

#### 3.2 — Filtered Retrieval & Search

**Question.** You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

In REST, paths are for identifying resources and query strings are for filtering them. `/api/v1/sensors` is the sensor collection. `/api/v1/sensors?type=CO2` is the same collection filtered — the resource itself has not changed.

If you put the filter in the path like `/api/v1/sensors/type/CO2`, it looks like `type` is some kind of container with `CO2` inside it, which is not how the domain works. It also gets messy fast — you would need `/sensors/status/ACTIVE`, `/sensors/room/LIB-301`, and then every combination like `/sensors/type/CO2/status/ACTIVE/room/LIB-301`. Each one needs its own route. With query parameters, it is just one method. Here is how it looks in `resource/SensorResource.java` — if I wanted to add a `status` filter, I would just add another `@QueryParam`:

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

Query parameters also handle the "show everything" case for free — just leave the parameter out. With path-based filtering, you would need something like `/sensors/type/all` which is ugly and could clash with a real sensor type called "all".

Bottom line: use path segments for actual resources (a specific sensor by ID) and query parameters for filtering, sorting, or pagination.

---

### Part 4: Deep Nesting with Sub-Resources

#### 4.1 — The Sub-Resource Locator Pattern

**Question.** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., `sensors/{id}/readings/{rid}`) in one massive controller class?

A sub-resource locator is a method with `@Path` but without `@GET`, `@POST`, etc. It does not handle the request — it returns an object whose methods do. Here is the one in `resource/SensorResource.java`:

```java
@Path("{sensorId}/readings")
public SensorReadingResource readingsSubResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

When someone hits `/sensors/{sensorId}/readings`, Jersey calls this method, gets a `SensorReadingResource` back, and lets it take over.

**Separation of concerns.** `SensorResource` handles sensors. `SensorReadingResource` handles readings. Neither touches the other's logic. Without the pattern, everything goes into one big class with sensor methods and reading methods mixed together — it gets messy fast.

**Scoped parent context.** The locator passes just the `sensorId` to the constructor: `new SensorReadingResource(sensorId)`. The sub-resource looks up the actual sensor through `SensorReadingService` only when it needs it. If the sensor does not exist, the service throws `NotFoundException` and `GlobalExceptionHandler` returns a 404. Since the sub-resource only holds the ID (not a `Sensor` object), it always gets fresh state even if another thread changed the sensor in between. The business logic — MAINTENANCE check, UUID generation, updating `currentValue` — all lives in `SensorReadingService.recordNewReading`, so the resource class stays thin.

**Scales well.** If readings ever needed their own sub-resource (like `/sensors/{id}/readings/{rid}/annotations`), I would just add another locator inside `SensorReadingResource`. The parent does not change. Testing is also simpler since `SensorReadingResource` is a plain Java class — you can instantiate it directly without starting Jersey.

---

### Part 5: Advanced Error Handling, Exception Mapping & Logging

#### 5.1 — Dependency Validation (422)

**Question.** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

Three status codes could apply, and they each mean different things. **404** means the URL does not exist. **400** means the request itself is broken — bad JSON, missing fields, wrong types. **422** means the request is fine syntactically but fails a business rule.

Look at this payload sent to `POST /api/v1/sensors`:

```json
{
  "id": "HEAT-999",
  "roomId": "NOT-A-ROOM",
  "type": "Temperature",
  "status": "ACTIVE",
  "currentValue": 19.0
}
```

This is a 422 situation. The URL `/sensors` exists, so 404 is wrong. The JSON is valid with all the right fields, so 400 does not fit. The issue is that `roomId` points to a room that was never created — the syntax is fine but a business rule fails.

In the code, `SensorService.registerSensor` throws `LinkedResourceNotFoundException` when `roomRepo.findById` returns nothing, and `InvalidReferenceMapper` maps it to 422 with a JSON `ErrorMessage`. The client knows: your request was understood, sending it again will not help, either create that room first or fix the `roomId`. If I returned 404, the client would think `/sensors` is gone. If I returned 400, they would look for a syntax error that is not there.

#### 5.2 — The Global Safety Net (500)

**Question.** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

If a stack trace leaks to the client, an attacker gets free information about the system. There are four things they can pull from it:

**Package structure.** A frame like `com.mycompany.new_cw.service.RoomService.decommissionRoom` shows the root package, the three-layer layout (`resource`, `service`, `repository`), and the exact line that failed. They now know how the code is organized without decompiling anything.

**Libraries and versions.** Frames from third-party code — like `org.glassfish.jersey.server.ServerRuntime$1.run` or `com.fasterxml.jackson.databind.ObjectMapper.readValue` — reveal which libraries are in use. An attacker can look up known vulnerabilities for those specific versions.

**Logic bugs.** Something like `NullPointerException` at `SensorRoom.removeRoom:30` tells the attacker there is a null dereference at that line. They can craft requests to hit it deliberately, either to crash the service or probe for something deeper.

**Server details.** Stack traces can include file paths, OS usernames, or container config. None of that is useful to a normal API user, but it all helps an attacker.

In this project, `exception/GlobalExceptionHandler.java` stops all of this. It implements `ExceptionMapper<Throwable>` as the catch-all. Known `WebApplicationException` types (400, 404, 405, 406, 415) keep their status code. Everything else gets logged with `java.util.logging` and the client just sees a 500 with "An unexpected server-side error has occurred. The incident has been logged for investigation." No class names, no versions, no line numbers leave the server.

#### 5.3 — API Request & Response Logging Filters

**Question.** Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every single resource method?

The main reason is that logging is not part of the business logic — it is a cross-cutting concern that applies to every request regardless of which endpoint handles it. Putting `Logger.info()` calls inside every resource method means duplicating the same logging code across `SensorRoom`, `SensorResource`, `SensorReadingResource`, and `DiscoveryResource`. If I wanted to change the log format or add a new field, I would have to update every single method.

With a JAX-RS filter, the logging lives in one place. `filter/RequestResponseLoggingFilter.java` implements both `ContainerRequestFilter` and `ContainerResponseFilter` and is annotated with `@Provider`, so Jersey automatically applies it to every request. The request filter logs the HTTP method and URI (`[INCOMING] GET http://...`), and the response filter logs the method, URI, and status code (`[OUTGOING] GET http://... -> 200`). Adding a new endpoint does not require touching the logging code — the filter picks it up automatically.

This also keeps the resource classes focused. `SensorRoom.listAllRooms()` just handles rooms — it does not know or care about logging. That separation makes both the resource and the filter easier to read, test, and change independently. If I later wanted to add timing information or request IDs to the logs, I would change one filter class instead of editing every method in the project.
