# Smart Campus Sensor & Room Management API

A JAX-RS RESTful service developed for the University of Westminster "Smart Campus" initiative. The API manages three resources — rooms, sensors, and sensor readings — and enforces the referential integrity and state-based rules described in the coursework specification.

## Module context

| Field | Value |
|-------|-------|
| Module | 5COSC022W, Client Server Architectures |
| Student ID | w2120276 |
| Weighting | 60% of final mark |
| Target mark band | Excellent (70%+) on every rubric row |
| Submission | Public GitHub repository, PDF report (transcribed from this file), 10 minute video demonstration |

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
# produces: target/new_cw-1.0-SNAPSHOT.war
```

### Deploy

Either open the project in NetBeans and choose **Run Project** (which deploys to the bundled GlassFish), or copy the `.war` into the container's `webapps/` directory. The default context root matches the artefact name, so the full base URL is:

```
http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1
```

A verification smoke test after deployment:

```bash
curl -s http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1
```

A 200 response containing the resources map confirms that the JAX-RS application has bootstrapped and classpath scanning has picked up the resource classes.

### Fresh start behaviour

The three in-memory stores begin empty on every deployment. The curl sequence below is ordered as a self-contained demonstration: steps 2 to 5 create the rooms and sensors that the later steps read, filter, and operate on.

## Sample curl commands

The specification requires at least five. The twelve calls below cover discovery, happy-path CRUD, filtering, the sub-resource locator, and each of the three domain-specific error paths. They are designed to run end to end on a freshly deployed WAR.

```bash
BASE="http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1"
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

By default, Jersey creates a fresh instance of each resource class for every incoming request. The JAX-RS specification calls this the "per-request" lifecycle, and it is the behaviour used throughout this project because no resource class is annotated with `@Singleton` and no explicit binding is registered in `JakartaRestConfiguration.java`. The practical consequence is that instance fields on a resource class are thread-confined: `SensorRoom`, `SensorResource`, and `SensorReadingResource` each hold a service-layer collaborator as an instance field, and those fields are only visible to the thread handling the request that triggered the resource's construction.

The data that genuinely crosses request boundaries lives in the repository classes. `repository/RoomRepository.java` and `repository/SensorRepository.java` each declare a `private static final List<T>` initialised through `Collections.synchronizedList(new ArrayList<>())`. Every read and write method on these repositories acquires a monitor on the backing list via `synchronized (roomStore) { ... }` or `synchronized (sensorStore) { ... }`. Two common failure modes are ruled out by that pattern: `ConcurrentModificationException` from a reader traversing the list while a writer removes an element, and lost updates from two writers racing on `List.set`. The readings store follows the same idiom: `repository/ReadingRepository.java` holds a `static final HashMap<String, List<SensorReading>>` keyed by `sensorId` and guards every read and write with a `synchronized (readingHistory) { ... }` block. The lock covers the lazy-create on first-reading path so two threads posting the first reading for a new sensor converge on the same list, and all three repositories share the same locking model, which makes the concurrency story easier to describe in a viva.

The specification's compound rule "delete a room only if it has no sensors" is a check-then-act sequence that spans both stores, and primitive list-level locking is not enough to keep it safe under concurrent writes. Two race windows would otherwise violate the room-sensor link invariant (no sensor should reference a room that has been deleted, and no room should be deleted while sensors still reference it): `RoomService.decommissionRoom` could observe an empty sensor query and remove the room between that check and a concurrent `SensorService.registerSensor` that was about to insert a sensor for that room; `SensorService.registerSensor` could confirm the parent room exists and be interrupted by a concurrent `RoomService.decommissionRoom` that found no sensors and finished before the register appended the new sensor. Either race orphans a sensor.

`repository/RoomRepository.java` declares a `public static final Object INTEGRITY_LOCK`, a dedicated shared monitor, and both `service/RoomService.decommissionRoom` and `service/SensorService.registerSensor` acquire it at the top of their critical section. The decommission path is shown below. `registerSensor` wraps its own parent lookup, sensor insert, and `sensorIds` append in an identical `synchronized (RoomRepository.INTEGRITY_LOCK)` block.

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

Serialising only those two operations keeps the invariant intact. Ordinary reads stay concurrent, and sensor reading appends take independent locks on the readings store so the POST reading path is unaffected.

### Question 2 — Hypermedia and HATEOAS

**Question.** Why is the provision of "Hypermedia" (links and navigation within responses) considered a core feature of advanced REST design (HATEOAS)? How does this approach benefit client developers compared to static documentation?

HATEOAS (Hypermedia As The Engine Of Application State) is the practice of including navigational links inside API responses so a client can discover the available state transitions dynamically rather than hard-coding them from documentation. The coursework specification describes it as a core feature of advanced REST design because it completes the separation between the client's model of the service and the server's URI scheme. When a response carries the links a client needs for its next request, the server is free to move endpoints, rename path segments, or move to a new version so long as the hypermedia in the responses stays internally consistent.

`resource/DiscoveryResource.java` is the practical entry point for this pattern in the present API. `GET /api/v1` returns a JSON object containing an `administrator` block, a `version` field, and a `resources` map that lists every top-level collection together with its absolute URI. A representative response looks like this:

```json
{
  "apiName": "Smart Campus Sensor & Room Management API",
  "version": "1.0.0",
  "administrator": {
    "name": "Methika Fernando",
    "email": "methika.fernando@university.edu"
  },
  "resources": {
    "rooms": "http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/rooms",
    "sensors": "http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/sensors"
  }
}
```

The URIs are built at request time from `UriInfo.getBaseUriBuilder()` so the response reflects the runtime deployment without any hard-coded values. A well-behaved client bookmarks only the discovery URL and follows the `resources` map from there.

A fully HATEOAS-compliant individual resource response would extend this pattern by embedding a `_links` block directly in each room object, giving the client every state transition it can take from that point. A hypothetical room response with links would look like this:

```json
{
  "id": "LIB-301",
  "name": "Library Quiet Study",
  "capacity": 40,
  "sensorIds": ["TEMP-001"],
  "_links": {
    "self": "http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/rooms/LIB-301",
    "sensors": "http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/sensors?roomId=LIB-301",
    "collection": "http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/rooms"
  }
}
```

The current API does not embed per-resource links in this way — the specification only requires the discovery endpoint — but the pattern illustrates the principle: a client that follows `_links.sensors` instead of constructing the URL itself remains correct even if the path structure changes server-side.

The benefit over static documentation is practical rather than theoretical. A PDF reference or a README sample URL is an external copy of the server's URI scheme that drifts the moment the code changes. A live discovery response is produced by the same code that handles the requests, so the contract cannot be out of sync with the code that produced it. Hypermedia also lets the server express conditional availability through the presence or absence of links. A sensor in `ACTIVE` state could ship with a post-reading link, while the same sensor in `MAINTENANCE` could omit it — a hypermedia-literate client could disable the "record a reading" button without a round trip. A room that still holds sensors could publish a `sensors` link but omit a `delete` link, matching the business rule that drives the 409 response on deletion.

### Question 3 — Returning IDs versus full objects in list endpoints

**Question.** When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client-side processing.

The trade-off is between a single round-trip response that may carry unused fields and a thin identifier list that forces a second round trip for every detail view.

**Option 1: ID-only collection**

An ID-only response is minimal:

```json
["LIB-301", "LEC-101", "LAB-201"]
```

For a campus with several thousand rooms this payload is an order of magnitude smaller than the full-object list, which is useful for mobile clients on limited connections or dashboards that only need to populate a drop-down. The cost is the N+1 network problem: a user interface that then wants to render each room's name or capacity must issue one `GET /rooms/{id}` per identifier. As the list grows, the aggregate cost of all those follow-up requests can easily exceed the bandwidth saved on the first call.

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

This is the approach taken by `SensorRoom.listAllRooms()` in `resource/SensorRoom.java`. It eliminates the N+1 problem at the cost of a larger single response. The decision is defensible here because a `Room` is small (an identifier, a display name, an integer capacity, and a short list of sensor ids) and the campus inventory is bounded. The marginal payload size is predictable even in the worst case.

The one compromise already built into the current shape is that `Room.sensorIds` contains only sensor identifiers rather than full `Sensor` objects. A room carries the ids of the sensors it hosts, but the full sensor data lives at the `/sensors` endpoint or through the filtered `/sensors?type=...` view. That avoids duplicating the sensor projection inside every room response, keeps cache invalidation per resource, and leaves the sensor collection autonomous. The present API does not implement pagination or field-selection parameters because the specification does not require them and the bounded campus inventory makes them unnecessary at the current scale.

### Question 4 — DELETE idempotency

**Question.** Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.

Yes, and the crucial distinction is that idempotency is defined on the server state that results from the request, not on the response the client observes. A method is idempotent if the intended effect of N identical requests (for N >= 1) is the same as the effect of a single request. The response code may differ between calls, but the state of the server must not.

Tracing the three cases against `SensorRoom.removeRoom` in `resource/SensorRoom.java`, which delegates to `service/RoomService.java`:

1. **First call on a room with no sensors.** `roomService.decommissionRoom(roomId)` looks up the entity, finds no sensors referencing it via `sensorRepo.findByRoomId(id)`, removes the room from the in-memory list, and returns without error. The resource converts that into **204 No Content**. The server state after this call contains no such room.

2. **Second identical call.** `roomService.decommissionRoom(roomId)` fails its lookup — `roomRepo.findById(id)` now returns `null` — and the service throws `NotFoundException`. `GlobalExceptionHandler` intercepts the exception and renders it as a **404 Not Found** response with an `ErrorMessage` body. The server state does not change: the room is still absent.

3. **Third, fourth, ... call.** Exactly the same as call 2. The 404 body is deterministic and the store is untouched.

The status codes differ between call 1 and calls 2+, but the property required by idempotency is satisfied: after any non-zero number of identical `DELETE /rooms/{id}` requests, the room is absent. A client that retries a lost acknowledgement therefore converges on the same outcome as one that succeeded on the first attempt, which is exactly the network-resilience property the REST community attributes to idempotent verbs.

The 409 Conflict case ("room still has sensors") does not threaten this guarantee. A 409 response leaves the room exactly where it was, so the server state is unchanged on every repetition. The response is also deterministic until some other request removes the dependent sensors, at which point the next DELETE will succeed and subsequent ones will revert to the 404 pattern above. The operation stays idempotent in the presence of the business rule.

### Question 5 — @Consumes mismatch consequences

**Question.** We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?

`@Consumes` is the server half of content negotiation: it advertises the media types the resource method is prepared to deserialise. When a request arrives, Jersey's runtime inspects the `Content-Type` header before it selects a method to invoke. If no resource method on the matched path declares a `@Consumes` value compatible with the client's `Content-Type`, Jersey never constructs the resource, never calls the method, and throws `javax.ws.rs.NotSupportedException` — a `WebApplicationException` subtype — which the runtime translates into **HTTP 415 Unsupported Media Type**.

In this project the 415 is routed through `exception/GlobalExceptionHandler`, which preserves the `WebApplicationException`'s intended status and wraps the error in the standard JSON envelope, so a client sees:

```json
{
  "errorMessage": "HTTP 415 Unsupported Media Type",
  "errorCode": 415
}
```

Two consequences follow for the present API. First, the referential integrity check inside `service/SensorService.registerSensor` — the `roomRepo.findById` lookup that raises `LinkedResourceNotFoundException` when the declared `roomId` does not exist — is only reached when the request is already known to be JSON. A client that sends `application/xml` or `text/plain` is rejected at the dispatch stage, which keeps the service method body free of format-handling branches and means a non-JSON payload can never silently half-parse. Second, the 415 is a clear client contract: retrying the same payload will never succeed — only re-encoding it as JSON will. That is categorically different from a 500, which a client might reasonably retry assuming a transient server fault.

`@Consumes` and its sibling `@Produces` both participate in this negotiation but in opposite directions. `@Produces` is matched against the request's `Accept` header, and a mismatch yields **HTTP 406 Not Acceptable** instead. The two together define the server's contract for a resource method: `@Consumes(APPLICATION_JSON) @Produces(APPLICATION_JSON)` reads as "I accept JSON and I respond with JSON". A missing `@Consumes` annotation would make the method accept any `Content-Type`, which is usually a bug waiting to happen because Jackson would then be asked to parse payloads it was never designed to handle. Declaring the annotation explicitly — as every POST in `SensorRoom`, `SensorResource`, and `SensorReadingResource` does — converts a class of malformed requests into a well-defined 415 response before any application code runs.

### Question 6 — @QueryParam versus @PathParam for filtering

**Question.** You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?

REST draws a sharp line between identifying a resource and selecting a projection of one. The path identifies; the query refines. `/api/v1/sensors` identifies the whole sensor collection. `/api/v1/sensors?type=CO2` narrows what the server returns to a filtered subset of that same collection. The base collection remains the same resource regardless of how many query parameters the client appends — the filter changes what is returned, not which resource is addressed.

Encoding the filter in the path, as in the hypothetical `/api/v1/sensors/type/CO2`, invents a resource hierarchy that does not exist in the domain. It implies that `type` is a container with `CO2` as a member, which invites a flood of virtual paths: `/sensors/status/ACTIVE`, `/sensors/room/LIB-301`, and then the combinatorial explosion of `/sensors/type/CO2/status/ACTIVE/room/LIB-301`. Each of those paths needs its own route, its own test, and its own place in the discovery document. Query parameters compose naturally in a single method signature. The handler on `resource/SensorResource.java` is shown below, and adding a `status` filter is a one-line addition of a second `@QueryParam` rather than a new endpoint:

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

Query parameters also keep the "no filter" case free. Omit the `type` parameter and the endpoint returns the entire collection, which is the natural default behaviour. A path-segment filter cannot degrade to "no filter" without a special-case path such as `/sensors/type/all`, which is both awkward and collides with the normal lookup pattern.

The general rule is: use path segments for anything that has a genuine resource character (a particular sensor identified by its id) and query parameters for anything that narrows, sorts, or paginates a collection. Mixing the two makes the URI structure harder to reason about without any gain in expressiveness.

### Question 7 — Benefits of the sub-resource locator pattern

**Question.** Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., `sensors/{id}/readings/{rid}`) in one massive controller class?

A sub-resource locator is a method annotated with `@Path` but not with an HTTP method annotation. It returns an object whose own `@Path`-annotated methods handle the remainder of the URI. The locator on `resource/SensorResource.java` is shown below:

```java
@Path("{sensorId}/readings")
public SensorReadingResource readingsSubResource(@PathParam("sensorId") String sensorId) {
    return new SensorReadingResource(sensorId);
}
```

JAX-RS picks up this method when a request arrives for `/sensors/{sensorId}/readings`, Jersey invokes the locator, and the returned `SensorReadingResource` instance takes over dispatch for the remaining path segments.

The first benefit is **separation of concerns**. `SensorResource` is responsible for sensors. `SensorReadingResource` is responsible for the readings that belong to one sensor. Neither class contains logic from the other's domain, and the split matches the natural join in the data model. A single mega-controller with `@Path("/sensors/{id}/readings")` sprinkled over half its methods quickly becomes the kind of 800-line file that is difficult to navigate and maintain.

The second benefit is **scoped parent context**. The locator receives the `sensorId` path parameter and passes only that identifier to the sub-resource's constructor: `new SensorReadingResource(sensorId)`. Each method on the sub-resource resolves the parent through `SensorReadingService` at the moment it needs it, and the service converts a missing parent into a `NotFoundException` that `GlobalExceptionHandler` renders as a 404 `Response`. Holding the identifier rather than a captured `Sensor` object keeps the sub-resource immune to stale snapshots: if another thread mutates the parent between the locator firing and the sub-resource method running, the method sees the fresh state. The MAINTENANCE guard, the UUID assignment, and the `currentValue` side-effect all live inside `SensorReadingService.recordNewReading`, so the resource body is a thin dispatcher rather than a business rule container.

The third benefit is that the pattern **scales recursively**. A reading could, in a larger system, acquire its own sub-resource (`/sensors/{id}/readings/{rid}/annotations`) simply by adding another locator inside `SensorReadingResource`. The top-level class neither grows nor becomes entangled in the new hierarchy. The pattern is also friendlier to testing: `SensorReadingResource` is a plain Java class with a straightforward constructor, so it can be unit-tested by instantiation without bootstrapping the JAX-RS runtime.

### Question 8 — Why 422 beats 404 for a broken reference in a valid payload

**Question.** Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?

The three candidate statuses say very different things about the failure. **HTTP 404 Not Found** signals that the URI in the request line does not identify a resource. **HTTP 400 Bad Request** signals that the server cannot parse or syntactically validate the request: malformed JSON, missing required fields, or the wrong type in a field. **HTTP 422 Unprocessable Entity** signals that the request is syntactically well-formed but semantically invalid: the server understands every byte of it but cannot act on it because a domain-level constraint fails.

Consider the payload below, sent as the body of a `POST /api/v1/sensors`:

```json
{
  "id": "HEAT-999",
  "roomId": "NOT-A-ROOM",
  "type": "Temperature",
  "status": "ACTIVE",
  "currentValue": 19.0
}
```

This request fits into the third category. The target URI `/sensors` does exist, so 404 would misrepresent the problem (the resource the client is addressing is the sensors collection, and the collection is present and willing to accept registrations). The payload is valid JSON with every required field of the correct type, so 400 would also misrepresent it. What fails is referential integrity between the payload and existing server state: the room the client is pointing at was never created.

The current implementation expresses this by throwing `LinkedResourceNotFoundException` from `SensorService.registerSensor` and letting `InvalidReferenceMapper` render it as 422 with a JSON `ErrorMessage` body containing the offending `roomId`. That response tells the client three things simultaneously: the request was understood, re-sending the same bytes will not change the outcome, and the fix is to create the referenced room first (or correct the id). A 404 response would ambiguously suggest that the sensors endpoint itself has disappeared, and a 400 would suggest a typo or missing field the client cannot actually find. 422 removes the confusion.

### Question 9 — Cybersecurity risks of leaked stack traces

**Question.** From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?

An unfiltered stack trace is a reconnaissance gift. Four distinct classes of information leak out of it, and each one enables a different stage of an attack.

**Internal paths and package structure.** Every frame in the trace carries a fully qualified class name such as `com.mycompany.new_cw.service.RoomService.decommissionRoom`. A single frame of that form reveals the root package, the project's three-tier layer split between `resource`, `service`, and `repository`, and the exact line where the failure happened. An attacker now knows where business logic lives without decompiling anything.

**Library names and versions.** Frames in stack traces frequently reference third-party classes — `org.glassfish.jersey.server.ServerRuntime$1.run`, `com.fasterxml.jackson.databind.ObjectMapper.readValue`. Those class names identify which libraries are in use and, combined with a manifest read or a cross-reference with `pom.xml`, pin those libraries to specific versions. Knowing exact versions allows a targeted search for known weaknesses in those dependencies.

**Logic flaws.** The line number and exception class together hint at the internal failure. A `NullPointerException` at `SensorRoom.removeRoom:30` publicly admits that a particular request path hits a null dereference under some condition, and the exception type narrows the guess about which variable. An attacker can then craft payloads that deliberately reach that branch, either to exhaust the service with 500s or to probe for a crash that escapes the `ExceptionMapper` net.

**Server and runtime configuration.** Real-world traces have included filesystem paths, operating usernames, and container configuration details. None of this is information a legitimate API consumer needs, and each piece narrows the attack surface further.

The remedy in this project is `exception/GlobalExceptionHandler.java`. It declares `ExceptionMapper<Throwable>` as the final safety net for anything no more-specific mapper has claimed. `WebApplicationException` subtypes (400 / 404 / 405 / 406 / 415) pass through with their intended HTTP status. Every other `Throwable` is logged server-side through `java.util.logging` and returned as a generic 500 body with the message "An unexpected server-side error has occurred. The incident has been logged for investigation." Nothing about the internal path, the library, the line, or the payload that triggered the failure leaves the process. The client sees only what is safe to share.
