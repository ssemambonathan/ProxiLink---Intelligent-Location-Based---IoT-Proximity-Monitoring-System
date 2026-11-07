# ProxiLink — Software Requirements Specification

Version 1.0 · 2026-11-07

## 1. Purpose and scope

ProxiLink is a location-aware IoT proximity monitoring platform. It combines four
software components and one optional hardware component:

| Component | Responsibility |
| --- | --- |
| Android app (Kotlin/Compose) | BLE scanning, RSSI smoothing and proximity classification, GPS capture, offline outbox, real-time updates |
| Spring Boot backend | REST + WebSocket API, authentication, organization-scoped data, event validation, analytics, audit |
| React admin dashboard | Live monitoring, interactive map, device management, analytics, administration |
| Event simulator | Synthetic BLE/GPS events for demonstrations without hardware |
| ESP32 beacon (optional) | BLE advertisement and GATT telemetry for a physical asset or location anchor |

The system answers three questions for an operator:

1. Which registered IoT devices are near this phone, and how strong is the signal?
2. Where was the phone when that observation happened?
3. What does the organization's fleet look like over time?

### 1.1 What the system deliberately does not claim

- **RSSI is not distance.** Proximity bands and metre estimates are approximations
  affected by walls, bodies, antenna orientation, enclosure materials and 2.4 GHz
  interference. The UI and API label them as estimates.
- **GPS is not indoor positioning.** Location is captured from the platform
  location provider; indoors accuracy degrades or the fix disappears.
- **BLE MAC addresses are not always available.** Android 12+ may expose
  `02:00:00:00:00:00` or random addresses depending on the peripheral and the
  scan result. The app treats identifiers as "best available" and does not
  attempt to bypass OS restrictions.
- **No silent tracking.** Location sharing and scanning are opt-in and visible in
  the app; the backend only stores data the client sends with a valid token.

## 2. Definitions

| Term | Meaning |
| --- | --- |
| BLE | Bluetooth Low Energy |
| RSSI | Received Signal Strength Indicator, dBm (negative; closer to 0 is stronger) |
| Smoothed RSSI | Median-of-5 followed by an exponential moving average (α = 0.3) |
| Proximity band | IMMEDIATE / VERY_CLOSE / NEARBY / FAR / OUT_OF_RANGE |
| Event | Immutable record of a proximity observation or a location fix |
| Outbox | Durable local queue of events awaiting upload |
| Organization | Tenant boundary; users and devices belong to exactly one organization |
| Recognized device | A BLE observation matching a device registered in the caller's organization |

Default proximity thresholds (configurable by an administrator at runtime):

| Band | Smoothed RSSI |
| --- | --- |
| IMMEDIATE | ≥ −50 dBm |
| VERY_CLOSE | ≥ −65 dBm |
| NEARBY | ≥ −80 dBm |
| FAR | ≥ −95 dBm |
| OUT_OF_RANGE | < −95 dBm or unknown |

The distance estimate uses the log-distance path-loss model
`d = 10^((txPower − RSSI) / (10·n))` with `txPower = −59 dBm` and `n = 2.0`. The
dashboard and API present it as an estimate, never as a measurement.

## 3. Actors

| Actor | Description |
| --- | --- |
| Operator (`USER`) | Registers devices, scans, shares location, reviews own organization's history |
| Administrator (`ADMIN`) | Everything an operator can do, plus user management, settings, audit logs and cross-organization visibility |
| IoT beacon / ESP32 | Advertises a service UUID and optional telemetry; does not authenticate to the backend |
| Event simulator | Authenticated client that generates clearly labelled synthetic events |

## 4. Functional requirements

Status legend: **Implemented** = code exists and is exercised by automated tests
or a verified manual run; **Reference** = code/config supplied but not verified in
this environment (see §8).

### 4.1 Authentication and accounts (FR-A)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-A1 | Register with email, password, display name and optional organization | Implemented |
| FR-A2 | Passwords ≥ 10 characters with at least one letter and one digit; stored as BCrypt(12) hashes | Implemented |
| FR-A3 | Login issues a 30-minute JWT access token and a rotating 14-day refresh token | Implemented |
| FR-A4 | Refresh tokens are single-use; replay is rejected | Implemented |
| FR-A5 | Logout revokes the refresh token | Implemented |
| FR-A6 | Disabled accounts cannot log in and existing access tokens stop working | Implemented |
| FR-A7 | `GET /auth/me` returns the caller's profile; password hashes are never serialized | Implemented |

### 4.2 Android device discovery (FR-B)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-B1 | Scan for BLE advertisements and show name, address (when provided), RSSI, service UUIDs and last-seen time | Implemented (device UI not executed here) |
| FR-B2 | Categorize observations as recognized or unknown using the backend identify endpoint | Implemented |
| FR-B3 | Register a recognized device into the caller's organization | Implemented |
| FR-B4 | Show scanning status, animation and discovered-device count | Implemented |
| FR-B5 | Respect Android permission differences across API 26–35+ | Implemented (see permissions guide) |
| FR-B6 | Stop scanning when the user leaves the screen or disables monitoring | Implemented |
| FR-B7 | Never fabricate a MAC address when the OS withholds it | Implemented |

### 4.3 Proximity engine (FR-C)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-C1 | Collect RSSI samples and reject outliers | Implemented |
| FR-C2 | Smooth with median-of-5 + EMA(α = 0.3) | Implemented (Android unit tests) |
| FR-C3 | Classify into five configurable bands | Implemented (backend tests; Android tests) |
| FR-C4 | Estimate distance with the log-distance model and label it as an estimate | Implemented |
| FR-C5 | Detect disappearance/out-of-range observations | Implemented |
| FR-C6 | Generate one event per observation with a client-generated UUID | Implemented |
| FR-C7 | Persist configuration history and device status transitions | Implemented |

### 4.4 GPS location (FR-D)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-D1 | Request location permission and show rationale before capture | Implemented |
| FR-D2 | Capture latitude, longitude, accuracy, altitude, speed and timestamp when available | Implemented |
| FR-D3 | Allow the user to enable/disable location sharing independently of BLE scanning | Implemented |
| FR-D4 | Use a configurable update interval (default 30 s) and battery-conscious priority | Implemented |
| FR-D5 | Handle permission denial, disabled location services and missing fixes gracefully | Implemented |
| FR-D6 | Display current and historical positions on an OpenStreetMap map | Implemented (UI not executed here) |
| FR-D7 | Never upload location when sharing is disabled | Implemented |

### 4.5 IoT device management (FR-E)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-E1 | Create, read, update, activate, deactivate and remove devices | Implemented (backend tested) |
| FR-E2 | Fields: name, type, category, hardware id, BLE MAC, service UUID, owner, organization, last seen/RSSI/proximity/location, metadata | Implemented |
| FR-E3 | Statuses ONLINE, OFFLINE, NEARBY, OUT_OF_RANGE, UNREGISTERED, INACTIVE | Implemented |
| FR-E4 | Duplicate hardware ids rejected within an organization | Implemented |
| FR-E5 | Stale devices become OFFLINE after a configurable timeout (default 300 s) | Implemented |
| FR-E6 | Identify by hardware id, BLE MAC or service UUID without leaking other tenants | Implemented |
| FR-E7 | ESP32 beacon advertisements and GATT telemetry | Reference firmware |

### 4.6 Synchronization (FR-F)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-F1 | Batch upload of up to 500 proximity or location events | Implemented |
| FR-F2 | Idempotent ingest keyed by client `eventId` | Implemented |
| FR-F3 | Durable local outbox with retry and exponential backoff on the Android client | Implemented (unit-tested) |
| FR-F4 | Automatic flush when connectivity returns | Implemented |
| FR-F5 | Server re-derives proximity level and distance; client values are advisory | Implemented |
| FR-F6 | WebSocket pushes for proximity events, location events, device status and notifications | Implemented (integration-tested) |
| FR-F7 | Organization-scoped fan-out; administrators receive all organizations | Implemented (integration-tested) |
| FR-F8 | Invalid events are reported per item without failing the whole batch | Implemented |

### 4.7 Administration dashboard (FR-G)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-G1 | Overview: totals, online/offline, active proximity events, 24 h locations, category split | Implemented |
| FR-G2 | Live view of devices, proximity events and location events over WebSocket | Implemented |
| FR-G3 | Interactive map with markers, categories, selection details and historical trail | Implemented |
| FR-G4 | Device management: search, filters, pagination, create, edit, activate/deactivate, delete | Implemented |
| FR-G5 | Analytics: event trends, proximity distribution, online ratio, most active devices | Implemented |
| FR-G6 | Administration: users, roles, enable/disable, settings, audit logs | Implemented |
| FR-G7 | Responsive layout for desktop, tablet and mobile | Implemented |
| FR-G8 | Login, session refresh, route guards and 403/404 states | Implemented |

### 4.8 Notifications (FR-H)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-H1 | Create an alert when a device transitions from far/none to near | Implemented |
| FR-H2 | List notifications with unread filter, mark one/all read, ownership enforced | Implemented |
| FR-H3 | Push new notifications over WebSocket | Implemented |

### 4.9 Analytics and audit (FR-I)

| ID | Requirement | Status |
| --- | --- | --- |
| FR-I1 | Daily proximity/location/unique-device trends for a configurable window | Implemented |
| FR-I2 | Proximity distribution includes zero-count bands | Implemented |
| FR-I3 | Audit login success/failure, registration, device and settings changes, retention purge | Implemented |
| FR-I4 | Audit search by action, category, actor and time range with pagination | Implemented |

## 5. Non-functional requirements

### 5.1 Security

- JWT (HS256) with a secret of at least 32 bytes; production refuses ephemeral keys
  by configuration policy, and the app logs a warning when none is provided.
- BCrypt cost 12; refresh tokens stored only as SHA-256 hashes.
- Stateless sessions; CSRF disabled because no cookies are used for the API.
- Role-based authorization: `/users`, `/audit-logs`, `/settings`, `/admin` are ADMIN-only.
- Organization scoping on every device/event query; admin bypass is explicit.
- Per-IP token-bucket rate limiting (240 req/min general, 10/min auth by default).
- Input validation on every request DTO; consistent error envelope.
- CORS allow-list; WebSocket origin allow-list (defaults to `*` for local demos,
  must be restricted in production).
- Audit trail for security-relevant actions.
- No secrets committed; `.env` and keystores are ignored; demo credentials exist
  only in the `dev` profile.

### 5.2 Privacy

- BLE scanning and location sharing are separate user-controlled switches.
- Location is only uploaded while sharing is enabled.
- Data retention is configurable (default 90 days) and enforced by a daily job.
- The dashboard only shows devices/events from the signed-in user's organization
  unless the user is an administrator.
- Identifiers unavailable due to OS privacy rules are displayed as unavailable,
  never guessed.

### 5.3 Performance and reliability

- BLE scans use the low-latency scan mode only while the user-started monitoring
  service (or a foreground scan screen) is active; the persistent notification
  makes the scan observable and offers a stop action. The battery cost of
  low-latency scanning is documented rather than hidden.
- RSSI processing is O(1) per sample with bounded buffers.
- Event ingestion is batched; database writes are transactional per request.
- Idempotency prevents duplicates after retries.
- Indexes cover organization/time, device/time, event id and status queries.
- The backend uses HikariCP defaults, pagination caps (100) and a bounded batch size (500).
- Device staleness is evaluated every 60 seconds.

### 5.4 Usability and accessibility

- Dark-first Material 3 design with a light theme, consistent spacing and typography.
- Loading skeletons, empty states and explicit error messages.
- Content descriptions on interactive icons; minimum touch targets; contrast
  checked against WCAG AA for primary text.
- Proximity and distance are always accompanied by an "estimate" caveat.

### 5.5 Maintainability

- API contract (`documentation/api-contract.md`) is the single source of truth
  shared by Android, dashboard and backend.
- Backend organized by feature package (auth, device, event, realtime, admin, ...).
- Flyway migrations are the schema source of truth; `ddl-auto` is `none`/`validate`.
- Automated tests cover classification boundaries, auth, tenancy, idempotency,
  RBAC, rate limiting and WebSocket fan-out.

## 6. External interfaces

| Interface | Detail |
| --- | --- |
| REST | JSON over HTTP, base `/api/v1`, Bearer JWT |
| WebSocket | `/ws?token=<accessToken>`, JSON text frames |
| Database | H2 (dev/test) and PostgreSQL 16 (prod) via JDBC |
| Cache | Caffeine in-process; Redis optional (disabled by default) |
| Map tiles | OpenStreetMap raster tiles (no API key) |
| BLE | Android Bluetooth LE APIs; ESP32 advertisements/GATT |
| Location | Google Play Services Fused Location Provider |

## 7. Constraints and assumptions

- Android 8.0 (API 26) minimum; target/compile SDK per the app module.
- The backend must be reachable over HTTPS/WSS in production; plain HTTP is for
  local development only.
- A single backend instance is assumed for in-memory rate limiting and the
  in-process WebSocket registry. Scaling out requires Redis-backed rate limits
  and a pub/sub bridge; this is documented as a limitation.
- Demo accounts and seeds are dev-profile only.
- No physical Android device, BLE peripheral or ESP32 was available in the
  development environment; see §8.

## 8. Verification status and limitations

| Area | Verified how | Limitation |
| --- | --- | --- |
| Backend REST, auth, RBAC, tenancy, idempotency, rate limit, WebSocket | 50 automated tests (`./gradlew test`) plus manual curl/Node WebSocket runs | H2 in PostgreSQL compatibility mode, not a live PostgreSQL instance |
| Dashboard build and API integration | TypeScript build and production bundle | UI interaction verified in a browser against the local backend (see testing report) |
| Android unit tests and APK build | Gradle unit tests and `assembleDebug` | No emulator/AVD or physical device available, so screens and BLE/GPS flows were not executed on-device |
| ESP32 firmware | Source review only | No Arduino/ESP32 toolchain or board available; not compiled or flashed |
| Simulator | Executed against the running backend | Synthetic by definition; every event is tagged `simulated=true` |
| Docker deployment | Files provided and reviewed | Docker daemon unavailable in this environment; not executed |

These limitations are repeated in the README and testing report rather than hidden.

## 9. Out of scope

- Indoor positioning (trilateration across multiple anchors), RTLS-grade accuracy.
- Cellular or Wi-Fi based positioning.
- OTA firmware updates, device provisioning security (ESP32 secure boot).
- Billing, multi-region replication, horizontal WebSocket scaling.
- Native iOS client.

## 10. Acceptance criteria

The system is accepted when the following sequence works end to end:

1. A user signs in and grants Bluetooth/location permissions.
2. The app scans, discovers a registered beacon and classifies its proximity.
3. A proximity event (and, when enabled, a location event) is created and uploaded.
4. The backend validates, deduplicates and stores the event.
5. The dashboard receives the event over WebSocket and displays it on the live
   view and map.
6. An administrator can inspect the device and its event history in the dashboard.

Steps 1–2 are implemented in the Android app but were not executed on a physical
device in this environment; steps 3–6 were executed with both the simulator and
automated tests. The simulator provides a labelled substitute for step 2 during
demonstrations.
