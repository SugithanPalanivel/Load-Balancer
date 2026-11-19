# Dispatch Load Balancer

Spring Boot (Java 17) REST API that allocates delivery orders to a vehicle fleet. It respects vehicle
capacity, assigns HIGH priority orders first, and minimizes travel distance using the Haversine formula.
No UI: test with Postman or Swagger UI.

## Run locally

Requirements: JDK 17+ and Maven 3.9+.

```bash
mvn clean test            # unit + integration tests (JaCoCo report: target/site/jacoco/index.html)
mvn spring-boot:run       # starts on http://localhost:8080
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

Docker:

```bash
docker build -t dispatch-load-balancer .
docker run -p 8080:8080 dispatch-load-balancer
```

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/dispatch/orders` | Upsert delivery orders (by `orderId`) |
| POST | `/api/dispatch/vehicles` | Upsert vehicles (by `vehicleId`) |
| GET | `/api/dispatch/plan` | Compute and return the dispatch plan |

Request and response bodies follow the assignment spec. Ready-to-paste payloads for the 30 orders and
5 vehicles in the brief are in `sample-data/`.

Plan response extras beyond the spec: `assignedOrders` is in visiting order, each vehicle also shows
`currentAddress` and `capacity`, and `unassignedOrders` lists orders that could not be placed with a reason.
`totalDistance` is a string like `"19.80 km"`.

### Error handling

| Case | Status |
|---|---|
| Missing/blank field, latitude outside -90..90, longitude outside -180..180, weight or capacity <= 0, empty list | 400 with a per-field `errors` list |
| Malformed JSON, unknown priority (accepted: HIGH, MEDIUM, LOW, any case) | 400 |
| Duplicate `orderId`/`vehicleId` within one request | 400 |
| `GET /plan` before orders or vehicles exist | 409 |
| Order heavier than every vehicle, or no remaining capacity | 200, listed in `unassignedOrders` |

Error body: `{"status":"error","message":"...","errors":["orders[0].latitude: ..."]}`

## Algorithm (`GreedyInsertionDispatchStrategy`)

1. Sort orders: HIGH, then MEDIUM, then LOW; heavier first within a priority.
2. For each order, try every vehicle with enough spare capacity and find its cheapest insertion point in
   that vehicle's route (extra Haversine km). Choose the vehicle with the smallest extra distance; ties go
   to the vehicle with more spare capacity.
3. Improve each route with 2-opt.

Routes are open: they start at the vehicle's current location and end at the last drop. It is a heuristic,
not an exact solver (this is a capacitated VRP, which is NP-hard). Because priority is processed first,
LOW orders are the ones left unassigned when total weight exceeds fleet capacity.
On the sample data (645 total weight vs 500 capacity), all HIGH and MEDIUM orders are assigned and 7 LOW
orders are reported as unassigned.

## Design

- `controller` thin REST layer, `service` orchestration and validation, `strategy` pluggable algorithm
  (Strategy pattern behind `DispatchStrategy`), `repository` Spring Data JPA, `dto` request/response records,
  `exception` global `@RestControllerAdvice`.
- Upserts use `saveAll` with JPA merge on the business id; Hibernate JDBC batching is enabled.
- H2 in-memory by default. Set `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD` (and add the driver) to use
  Postgres. The plan is computed on demand from stored data.

## Deploy (Render, Docker)

1. Push this folder to a GitHub repo.
2. Render: New > Web Service > connect repo > Runtime: Docker. Render sets `PORT`, which the app reads.
3. Live URL: `https://<service>.onrender.com`, Swagger at `/swagger-ui.html`.
