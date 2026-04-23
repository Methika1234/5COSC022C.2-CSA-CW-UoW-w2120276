# Smart Campus Sensor & Room Management API

## API Overview

This is a RESTful API built with **JAX-RS (Jakarta RESTful Web Services)** for the university's "Smart Campus" initiative. It manages campus **Rooms** and their deployed **Sensors** (temperature monitors, CO2 detectors, occupancy trackers, smart lighting controllers) along with historical sensor reading data.

### Architecture

The application follows a **three-tier layered architecture**:

| Layer | Package | Role |
|-------|---------|------|
| **Resource** | `resource` | JAX-RS annotated endpoint classes handling HTTP routing |
| **Service** | `service` | Business logic, validation, and referential integrity enforcement |
| **Repository** | `repository` | Thread-safe in-memory persistence using synchronized Java collections |

Supporting packages:
- `model` — POJOs: `Room`, `Sensor`, `SensorReading`, `ErrorMessage`
- `exception` — Custom exceptions + `@Provider` exception mappers
- `filter` — JAX-RS request/response logging filter

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 11 |
| Framework | JAX-RS 2.1 (Jakarta EE 8) |
| Build Tool | Apache Maven |
| Packaging | WAR (Web Application Archive) |
| Server | GlassFish 5 / Payara 5 |
| Data Storage | In-memory (`ArrayList`, `HashMap`) |

### Endpoints

| Method | URI | Description |
|--------|-----|-------------|
| `GET` | `/api/v1` | API discovery — metadata, version, resource links |
| `GET` | `/api/v1/rooms` | Retrieve all rooms |
| `POST` | `/api/v1/rooms` | Register a new room (returns 201 + Location) |
| `GET` | `/api/v1/rooms/{roomId}` | Fetch a specific room |
| `DELETE` | `/api/v1/rooms/{roomId}` | Decommission a room (blocked if sensors remain) |
| `GET` | `/api/v1/sensors` | List sensors (supports `?type=` filter) |
| `POST` | `/api/v1/sensors` | Register a new sensor (validates roomId exists) |
| `GET` | `/api/v1/sensors/{sensorId}/readings` | Fetch reading history for a sensor |
| `POST` | `/api/v1/sensors/{sensorId}/readings` | Record a new reading (updates parent sensor value) |

### Error Handling Strategy

| Status | Exception | Trigger |
|--------|-----------|---------|
| **409** Conflict | `RoomNotEmptyException` | Deleting a room with deployed sensors |
| **422** Unprocessable Entity | `LinkedResourceNotFoundException` | Sensor creation with invalid roomId |
| **403** Forbidden | `SensorUnavailableException` | Recording a reading on a MAINTENANCE sensor |
| **500** Internal Server Error | `GlobalExceptionHandler` | Catch-all for unexpected runtime errors |

---

## How to Build and Run

### Prerequisites

- Java 11+ JDK
- Apache Maven 3.6+
- GlassFish 5 or Payara 5

### Build

```bash
git clone <repository-url>
cd new_cw
mvn clean package
```

Output: `target/new_cw-1.0-SNAPSHOT.war`

### Deploy

Copy the WAR into the auto-deploy directory:

```bash
cp target/new_cw-1.0-SNAPSHOT.war $GLASSFISH_HOME/glassfish/domains/domain1/autodeploy/
asadmin start-domain
```

Or deploy via the GlassFish Admin Console at `http://localhost:4848`.

### Base URL

```
http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1
```

---

## Sample curl Commands

### 1. API Discovery

```bash
curl -s http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1
```

### 2. Create a Room

```bash
curl -X POST http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LIB-301","name":"Library Quiet Study","capacity":40}'
```

### 3. List All Rooms

```bash
curl -s http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/rooms
```

### 4. Fetch a Room by ID

```bash
curl -s http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/rooms/LIB-301
```

### 5. Register a Sensor

```bash
curl -X POST http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-001","type":"Temperature","status":"ACTIVE","currentValue":21.5,"roomId":"LIB-301"}'
```

### 6. List Sensors Filtered by Type

```bash
curl -s "http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/sensors?type=Temperature"
```

### 7. Record a Sensor Reading

```bash
curl -X POST http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/sensors/TEMP-001/readings \
  -H "Content-Type: application/json" \
  -d '{"value":22.8,"timestamp":1713352800000}'
```

### 8. Fetch Sensor Reading History

```bash
curl -s http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/sensors/TEMP-001/readings
```

### 9. Attempt to Delete Room with Sensors (expect 409)

```bash
curl -X DELETE http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/rooms/LIB-301 -v
```

### 10. Register Sensor with Invalid Room (expect 422)

```bash
curl -X POST http://localhost:8080/new_cw-1.0-SNAPSHOT/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-999","type":"CO2","status":"ACTIVE","currentValue":400,"roomId":"NONEXISTENT"}'
```

---

## Report: Answers to Coursework Questions

---

### Part 1: Service Architecture & Setup

#### Q1.1: Default Lifecycle of a JAX-RS Resource Class

By default, JAX-RS resource classes follow a **per-request lifecycle**. The JAX-RS runtime instantiates a **new object** of the resource class for every incoming HTTP request. After the response is sent, the instance becomes eligible for garbage collection.

This design means instance fields are **never shared** between concurrent requests. To maintain persistent data across requests, our application declares the backing data structures (`ArrayList` for rooms/sensors, `HashMap` for readings) as **`static` fields** inside the Repository classes. This ensures every resource instance, regardless of which thread created it, operates on the same shared data.

However, static shared state introduces **concurrency hazards**. Two requests arriving simultaneously could both read and write the same list, causing race conditions (e.g., a lost update or a `ConcurrentModificationException`). We address this with two strategies:

1. **Synchronized wrappers** — `Collections.synchronizedList()` makes individual operations (add, remove) atomic.
2. **Explicit `synchronized` blocks** — For compound operations such as "check if room has sensors, then delete" we acquire a shared `INTEGRITY_LOCK` to ensure the entire operation is atomic. This same lock coordinates sensor creation with room deletion, preventing a sensor from being assigned to a room that is simultaneously being removed.

---

#### Q1.2: HATEOAS and Its Benefits

HATEOAS (Hypermedia as the Engine of Application State) represents the highest maturity level of RESTful API design (Level 3 on the Richardson Maturity Model). It means the server embeds navigational links within its responses so that clients can dynamically discover available actions and resources.

**Benefits over static documentation:**

1. **Decoupled clients** — Clients follow server-provided URIs instead of constructing URLs from hard-coded path templates. If the server changes its URL scheme, compliant clients continue to work without code changes.
2. **Runtime discoverability** — Starting from a single root endpoint (`GET /api/v1`), a client can traverse the entire API by following links, much like a user browsing a website.
3. **Living documentation** — Embedded links are always current and reflect the real server state, unlike static Swagger or wiki pages that can drift out of date.
4. **Guided workflows** — The server can selectively include or exclude links based on the current resource state, guiding the client toward valid operations and away from invalid ones.

Our Discovery endpoint demonstrates this by returning dynamically constructed URIs for the `rooms` and `sensors` collections using `UriInfo.getBaseUriBuilder()`, so clients do not need to guess or hard-code the base path.

---

### Part 2: Room Management

#### Q2.1: Returning IDs vs. Full Objects

| Approach | Pros | Cons |
|----------|------|------|
| **IDs only** | Minimal payload; fast to serialize and transmit | Clients must issue N additional requests to retrieve details (N+1 problem), increasing latency and server load |
| **Full objects** (our choice) | Single round-trip provides all data; simpler client code | Larger payloads; may transfer fields the client does not need |

For a bounded domain like campus rooms, the collection size is manageable and returning full objects avoids the severe latency penalty of the N+1 pattern. For unbounded or very large datasets, the recommended compromise is **pagination** (`?page=1&size=20`) combined with optional **sparse fieldsets** (`?fields=id,name`) to let the client control the response granularity.

---

#### Q2.2: Idempotency of DELETE

Yes, our `DELETE /api/v1/rooms/{roomId}` is **idempotent**.

- **First call:** Room is found, passes the sensor check, and is removed. Response: **204 No Content**.
- **Second (identical) call:** Room no longer exists. The service throws `NotFoundException`. Response: **404 Not Found**.
- **All subsequent calls:** Identical to the second — 404.

The **server state** is the same after the first call as after any number of subsequent calls: the room does not exist. The HTTP specification defines idempotency in terms of the effect on server-side state, not the response code. A 204 on the first call and a 404 on the second are both correct responses that reflect the same underlying fact — the resource is gone. This contrasts with `POST`, which is non-idempotent because each call could create a new resource.

---

### Part 3: Sensor Operations & Linking

#### Q3.1: Consequences of Content-Type Mismatch with `@Consumes`

When a method is annotated with `@Consumes(MediaType.APPLICATION_JSON)`, JAX-RS performs **server-side content negotiation** before invoking the method:

1. The runtime reads the `Content-Type` header from the incoming request.
2. It checks whether the declared media type matches any type listed in `@Consumes`.
3. If there is no match (e.g., the client sends `text/plain` or `application/xml`), the runtime **rejects the request immediately** without ever calling the resource method.
4. The client receives an **HTTP 415 Unsupported Media Type** response.

This mechanism is handled entirely by the framework — no manual validation code is required. It guarantees that the `MessageBodyReader` responsible for deserializing JSON into a Java POJO is only invoked when the incoming data is actually JSON, preventing parsing errors and malformed-data bugs. If no `Content-Type` header is provided, JAX-RS defaults to `application/octet-stream`, which also does not match `application/json`, so the request is still rejected with 415.

---

#### Q3.2: `@QueryParam` vs. Path Segment for Filtering

**Query parameter:** `GET /api/v1/sensors?type=CO2`
- `/api/v1/sensors` is the collection resource.
- `?type=CO2` is an optional modifier that filters the result set.

**Path segment:** `GET /api/v1/sensors/type/CO2`
- Implies `/type/CO2` is a distinct sub-resource, which is semantically wrong — we are still querying the same sensor collection.

**Why query parameters are the correct design choice:**

| Criterion | Query Parameter | Path Segment |
|-----------|----------------|--------------|
| **Semantics** | Filters a collection (correct) | Identifies a resource (misleading) |
| **Optionality** | Inherently optional | Mandatory — omitting it changes the URL identity |
| **Composability** | `?type=CO2&status=ACTIVE` is natural | `/type/CO2/status/ACTIVE` creates a combinatorial explosion of routes |
| **HTTP caching** | Caches treat the same path with different query strings as variants of one resource | Different paths are entirely separate cache entries |
| **Industry convention** | Standard practice (GitHub API, Stripe API, etc.) | Rarely used for filtering |

Query parameters keep the URL structure clean and express the intent of "give me the same collection, but narrowed down" rather than "go to a different resource."

---

### Part 4: Deep Nesting with Sub-Resources

#### Q4.1: Benefits of the Sub-Resource Locator Pattern

In our implementation, `SensorResource` contains a locator method at `@Path("{sensorId}/readings")` that returns an instance of `SensorReadingResource`. This method has no HTTP-method annotation — it delegates entirely to the returned sub-resource class.

**Architectural benefits:**

1. **Single Responsibility** — `SensorResource` handles sensor CRUD; `SensorReadingResource` handles reading operations. Each class has one clear purpose, making the code easier to read, test, and debug.

2. **Reduced class complexity** — Without delegation, a single controller would accumulate methods for `/sensors`, `/sensors/{id}`, `/sensors/{id}/readings`, `/sensors/{id}/readings/{rid}`, and potentially more. This becomes unmanageable as the API grows.

3. **Independent evolution** — Adding a new nested resource (e.g., `/sensors/{id}/alerts`) requires only a new sub-resource class and a one-line locator method, without modifying existing code.

4. **Context injection** — The locator method validates the parent entity and passes context (`sensorId`) to the sub-resource constructor, centralizing the validation at the delegation point rather than repeating it in every nested endpoint.

5. **Parallel development** — Team members can work on different sub-resource classes independently with minimal merge conflicts.

---

### Part 5: Advanced Error Handling, Exception Mapping & Logging

#### Q5.2: Why HTTP 422 Is More Appropriate Than 404 for Payload Reference Issues

When a client sends `POST /api/v1/sensors` with a body containing a non-existent `roomId`:

- The **URL** (`/api/v1/sensors`) is valid and the endpoint exists.
- The **JSON syntax** is well-formed and parseable.
- The **semantic content** — specifically the `roomId` foreign-key reference — is invalid.

**HTTP 404** means "the target URI does not correspond to any resource." Using it here would mislead the client into thinking the sensors endpoint itself is missing or the URL is wrong.

**HTTP 422** (Unprocessable Entity) means "the server understands the content type and the syntax is valid, but it cannot process the instructions due to semantic errors." This precisely describes our scenario: the JSON is syntactically correct but contains a reference to a room that does not exist in the system.

This distinction helps client developers diagnose the issue immediately: they need to correct the `roomId` value in their request body, not change the URL they are calling.

---

#### Q5.4: Cybersecurity Risks of Exposing Stack Traces

Exposing raw Java stack traces to API consumers provides attackers with valuable reconnaissance data:

1. **Technology fingerprinting** — Stack frames reveal the language (Java), framework (Jersey/JAX-RS), and exact library versions, enabling targeted searches for known CVEs.

2. **Package and class structure** — Names like `com.mycompany.new_cw.service.RoomService.decommissionRoom()` expose the internal package hierarchy, class names, and method signatures.

3. **Server paths** — File paths in the trace (e.g., `/opt/payara/glassfish/...`) reveal the operating system, deployment layout, and server software.

4. **Business logic hints** — Method names and exception messages can reveal validation rules and execution flow, helping an attacker understand how to craft requests that bypass security controls.

5. **Dependency exposure** — Third-party library class names in the trace reveal the full dependency chain, expanding the attack surface to any vulnerability in any transitive dependency.

Our `GlobalExceptionHandler` mitigates this by catching all unhandled `Throwable` instances, logging the full details server-side for administrative debugging, and returning only a sanitized generic message to the client.

---

#### Q5.5: Advantages of JAX-RS Filters for Logging

Our `RequestResponseLoggingFilter` implements both `ContainerRequestFilter` and `ContainerResponseFilter`, providing centralized observability for every request and response.

**Advantages over manual `Logger.info()` calls in each resource method:**

1. **Guaranteed coverage** — Filters intercept every request/response automatically, even for newly added endpoints. Manual logging relies on developers remembering to add log calls, which is error-prone.

2. **Single point of change** — Modifying the log format, level, or destination requires editing one class instead of touching every resource method.

3. **Clean separation** — Resource methods focus purely on business logic. Logging is a cross-cutting concern that belongs in infrastructure code, not in domain logic.

4. **Consistency** — Every log entry follows the same format (`[INCOMING] GET /api/v1/rooms`, `[OUTGOING] GET /api/v1/rooms -> 200`), making log parsing, monitoring, and alerting reliable.

5. **DRY compliance** — Avoids duplicating identical logging boilerplate across dozens of methods. This follows the same philosophy as Aspect-Oriented Programming, where cross-cutting concerns are handled declaratively rather than manually.
