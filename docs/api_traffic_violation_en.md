# TrafficMind — API Documentation (Traffic Monitor & Violation Modules)

This document describes the external interfaces for the Traffic Monitor (dashboards/big-screen) and Violation & Law Enforcement modules. It was generated from the repository controllers and services and reorganized to show, for each module, the requested four interface sections.

**Global assumptions**
- Authentication: the project exposes `POST /api/auth/login` and `POST /api/auth/logout`. Protected APIs are assumed to use the HTTP header `Authorization: Bearer <token>` (JWT) for authentication.
- Real-time traffic and dashboard data are stored in Redis (see `RedisService`), using keys such as `intersection:{id}`, `dashboard:stats`, `dashboard:heatmap`.
- Violations are persisted in MySQL (via `ViolationRepository`) and cached in Redis with keys such as `violation:{id}` and list `violations:list`.
- WebSocket alerts are served at `/ws/alerts` and use plain text messages by default (component `AlertWebSocketHandler`).
- Timestamps use ISO-8601 strings (`LocalDateTime.toString()` produced by server).

Common HTTP statuses: 200 OK, 201 Created, 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 429 Too Many Requests, 500 Internal Server Error.

---

## Traffic Monitor Module (Traffic & Big-Screen)

1) User Interfaces
- Primary users: **Administrator** (dashboard, big-screen) and **Police officer** (realtime monitoring).
- Main user actions:
  - View intersection list and basic metrics (`GET /api/intersections`).
  - View realtime data for a specific intersection (`GET /api/intersections/{id}/realtime`).
  - View macro analytics for dashboards (`GET /api/dashboard/stats`).
  - View heatmap data for big-screen (`GET /api/dashboard/heatmap`).
  - (Test) Write intersection/dashboard data (`POST /api/intersections/{id}`, `POST /api/dashboard/stats`, `POST /api/dashboard/heatmap`) — intended for admin/internal tools or test harness only.

2) Hardware Interfaces
- Cameras / AI edge devices: provide single-frame images, metadata (vehicle counts, signal status) and evidence image upload to object storage (OSS/S3) or HTTP-accessible URLs.
- Video streaming sources: RTSP/HLS streams; backend returns stream URLs for direct playback on frontend.
- Network: devices must be able to reach backend or an ingestion gateway over a reliable network. Bandwidth required depends on video stream quality.

3) Software Interfaces
- Data stores:
  - MySQL: persistent storage of business entities (intersections, violations).
  - Redis: realtime caches and data used by the Traffic Monitor (`intersection:{id}`, `dashboard:stats`, `dashboard:heatmap`).
- Services / components:
  - `RedisService`: read/write realtime traffic and dashboard data, manage TTLs.
  - `TrafficMonitorController`: HTTP endpoints for monitor and dashboard.
  - Optional: Nginx reverse proxy / TLS termination.
- Data formats: Redis stores hashes and values; controller endpoints return JSON. Timestamps are ISO-8601 strings.

4) Communications Interfaces
- HTTP (REST) endpoints (base path `/api`):
  - `GET /api/intersections` — returns list of intersections and basic data from Redis.
  - `GET /api/intersections/{id}/realtime` — realtime data for a single intersection.
  - `GET /api/dashboard/stats` — macro analytics for dashboards.
  - `GET /api/dashboard/heatmap` — heatmap points for big-screen.
  - `POST /api/intersections/{id}` — test helper to set intersection data in Redis (TTL 5 min).
  - `POST /api/dashboard/stats` — test helper (stats TTL 1h).
  - `POST /api/dashboard/heatmap` — test helper (heatmap TTL 10min).

HTTP headers and security:
- `Authorization: Bearer <token>` is recommended for protected endpoints.
- Accept `application/json`. Test POST endpoints accept JSON bodies.

Examples (responses):
- `GET /api/intersections` response:
  ```json
  [
    {"id":"1","data":{"vehicleCount":"123","signalStatus":"GREEN","avgSpeed":"35.4","cameraStreamUrl":"rtsp://camera1/stream"}}
  ]
  ```
- `GET /api/intersections/{id}/realtime` response:
  ```json
  {"vehicleCount":"123","signalStatus":"GREEN","avgSpeed":"35.4","cameraStreamUrl":"rtsp://...","lastUpdated":"2024-12-10T12:34:56"}
  ```

Non-functional notes and recommendations (Traffic):
- Protect write endpoints; restrict `POST` to admin/internal systems.
- Apply rate limits to protect Redis and downstream systems.
- Deploy Redis in HA mode (Cluster or Sentinel).
- Log source IP and deviceId for traceability of ingested traffic data.

---

## Violation & Law Enforcement Module

1) User Interfaces
- Primary users: **Police Officer** (case handling, confirmation and enforcement) and **Administrator** (reporting, oversight).
- Main user actions and screens:
  - Violation List (`GET /api/violations?page=&size=`): paginated list with filters (intersection, status, date range, plate, violation type). Columns include `id`, `occurredAt`, `plateNumber`, `violationType`, `intersectionName`, `status`, `aiConfidence`, and `thumbnail` (from `imageUrl`). Actions: open detail, bulk operations, export (admin).
  - Violation Detail (`GET /api/violations/{id}`): full record and evidence viewer. Fields shown: `id`, `intersectionId`, `plateNumber`, `violationType`, `imageUrl` (preview/download), `aiConfidence`, `occurredAt`, `status`, `processedBy`, `processedAt`, `penaltyAmount`, `reviewNotes`, `appealStatus`, `createdAt`, `updatedAt`.
  - Process Violation Form (`PUT /api/violations/{id}/process`): officer fills `processedBy`, `penaltyAmount`, `reviewNotes` and submits. Client and server validation required.
  - Realtime Alerts Panel (WebSocket `/ws/alerts`): shows high-priority alerts; clicking an alert navigates to the corresponding violation detail (if `violationId` is present).
  - Admin / Audit Views: export, audit logs, cross-region queries and reporting.

  - Example ingestion payload (internal camera/AI):
  ```json
  {
    "intersectionId":"123",
    "plateNumber":"ABC123",
    "violationType":"RED_LIGHT",
    "imageUrl":"https://oss/.../evidence.jpg",
    "timestamp":1700000000000,
    "aiConfidence":0.92,
    "cameraId":"cam-01"
  }
  ```

  - Example process payload:
  ```json
  {
    "processedBy":"1001",
    "penaltyAmount":"200.00",
    "reviewNotes":"Evidence verified on site"
  }
  ```

- Permissions & access control:
  - `GET /api/violations*`: accessible to `ROLE_POLICE` and `ROLE_ADMIN` (with possible row-level filtering by region).
  - `PUT /api/violations/{id}/process`: only `ROLE_POLICE` or users with processing rights.
  - `POST /api/violations/report`: internal use only (camera/AI/gateway); not exposed in UI.

- Validation & error UX:
  - Client-side validation for required fields and formats; server returns friendly error messages with `message` and optional `errors` details.
  - Common error messages: 401 "Session expired, please log in again.", 403 "Insufficient permissions.", 400 "Invalid parameter: penaltyAmount format.", 500 "Server error, please try later."

- Acceptance criteria:
  - Lists support filtering and pagination reliably.
  - Detail page displays evidence and allows download/view.
  - Processing updates persist to DB and update Redis cache so UI reflects changes immediately.
  - WebSocket alerts surface in UI and link to details when `violationId` is provided.

- UX and performance notes:
  - Use lazy loading and thumbnails for images; support infinite scroll or efficient pagination.
  - Record evidence view/download actions for audit and compliance.

2) Hardware Interfaces
- Cameras / AI edge devices must produce evidence images (upload to object storage) and metadata (cameraId, timestamp, aiConfidence).
- Evidence storage: object storage (OSS/S3) recommended; backend stores URLs rather than binary blobs.

3) Software Interfaces
- Primary storage and caches:
  - MySQL via `ViolationRepository`: authoritative persistent store for violations.
  - Redis: realtime cache (`violation:{id}`), list (`violations:list`), and counters (`violation:total:today`).
- Services / components:
  - `ViolationService`: handles persistence to MySQL, caching to Redis, counter increments and conversion of entity to map.
  - `ViolationController`: exposes REST endpoints for report/query/process flows.
  - `RedisService`: used for counter and caching utilities in some parts.
- Domain fields (from service convertToMap): `id`, `intersectionId`, `plateNumber`, `violationType`, `imageUrl`, `aiConfidence`, `occurredAt`, `status`, `processedBy`, `processedAt`, `penaltyAmount`, `reviewNotes`, `appealStatus`, `createdAt`, `updatedAt`.

4) Communications Interfaces
- HTTP (REST) endpoints (base path `/api`):
  - `POST /api/violations/report` — internal ingestion from camera/AI service. Persists to MySQL and Redis; triggers WebSocket alert.
    - Request example:
    ```json
    {"intersectionId":"123","plateNumber":"ABC123","violationType":"RED_LIGHT","imageUrl":"https://oss/.../evidence.jpg","timestamp":1700000000000,"aiConfidence":0.92,"cameraId":"cam-01"}
    ```
    - Response example: `{"id":123,"message":"Violation reported successfully"}`

  - `GET /api/violations` — list violations (pagination `page`,`size`).
  - `GET /api/violations/{id}` — detailed violation record (cache-first then DB).
  - `PUT /api/violations/{id}/process` — officer processes violation; example body: `{"processedBy":"1001","penaltyAmount":"200.00","reviewNotes":"Evidence verified on site"}`.
  - `POST /api/violations/increment` — internal/test increment counter (`violation:total:today`).
  - `GET /api/violations/count` — read current counter value.

  - WebSocket alerts `/ws/alerts`:
    - Registered via `WebSocketConfig` with `registry.addHandler(alertWebSocketHandler, "/ws/alerts").setAllowedOrigins("*")`.
    - Protocol: plain WebSocket with text frames (server currently sends plain text alerts).
    - Connection examples: `ws://host/ws/alerts` (dev), `wss://host/ws/alerts` (prod).
    - Authentication: current code does not enforce it. Recommended options: `Authorization` header during handshake, short-lived `token` query parameter, or `Sec-WebSocket-Protocol`.
    - Message formats (recommended JSON):
      ```json
      {"type":"VIOLATION_ALERT","violationId":123,"violationType":"RED_LIGHT","intersectionId":"12","occurredAt":"2024-12-10T12:34:56","imageUrl":"https://...","aiConfidence":0.92,"severity":"HIGH"}
      ```

Security and operational recommendations (Violation):
- Restrict `POST /api/violations/report` to trusted sources (mTLS / API Gateway / IP whitelist / signed requests).
- Keep authoritative data in MySQL; use Redis for realtime performance but ensure cache expiry and consistency.
- Implement auditing for `process` actions: who/when/what.
- Retain evidence in object storage with immutability/versioning where required.

---

**Next steps**
- Generate OpenAPI/Swagger specification from these endpoints for the frontend and QA teams.
- Harden WebSocket handshake with authentication and implement role-based authorization (e.g., `ROLE_ADMIN`, `ROLE_POLICE`).

*Document generated and reorganized from repository controllers and services: `TrafficMonitorController`, `ViolationController`, `RedisService`, `ViolationService`, `AlertWebSocketHandler`, `WebSocketConfig`, `AuthController`.*
