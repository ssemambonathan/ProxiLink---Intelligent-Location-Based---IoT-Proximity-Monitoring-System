# ProxiLink — testing report

**Date:** 2026-11-07  
**Environment:** Windows 10 Pro 19045, x64, 8 GB RAM; Temurin JDK 21.0.13;
Gradle 8.14.3; Node.js 24.18.0; Chrome (headless); H2 2.3.232 in PostgreSQL
mode. No Android emulator, no physical phone, no ESP32 toolchain and no Docker
daemon were available.

This report distinguishes three things: what was **executed and passed**, what
was **executed and failed**, and what was **not executed**. Nothing in the last
category is presented as verified.

## 1. Summary

| Area | Result | Evidence |
| --- | --- | --- |
| Backend automated tests | **50/50 passed**, 0 failures, 0 skipped | `backend/build/test-results/test/` |
| Backend build | `bootJar` successful, jar boots with `dev` profile | `backend/backend.log`, `/actuator/health` = `UP` |
| Backend manual API smoke | Login, RBAC, devices, ingest, idempotency, WebSocket | Section 3 |
| Dashboard production build | `npm run build` successful | `admin-dashboard/dist/` |
| Dashboard browser checks | **14/14 checks passed**, 0 unexpected console errors | `documentation/screenshots/browser-verification.json` |
| Android unit tests | **36/36 passed** | `android-app/app/build/test-results/testDebugUnitTest/` |
| Android debug APK | Built, 22 MB, `targetSdk 36` | `android-app/app/build/outputs/apk/debug/app-debug.apk` |
| Android on-device/emulator run | **Not executed** | No emulator/AVD or device |
| Simulator against real API | 3/3 events accepted | Section 6 |
| ESP32 firmware | **Not compiled, not flashed** | No Arduino/ESP32 toolchain or board |
| Docker Compose | **Not executed** | Docker daemon unavailable |

## 2. Backend automated tests

Command:

```bash
cd backend
JAVA_HOME=/path/to/jdk-21 ./gradlew --no-daemon --max-workers=1 test
```

Result: `BUILD SUCCESSFUL`; 50 tests, 0 failures, 0 skipped. This is the exact
command of the final confirming run; see defect 7 below about a transient
executor crash under concurrent daemons.

| Test class | Tests | Coverage |
| --- | --- | --- |
| `AuthFlowIntegrationTest` | 10 | Registration, duplicate email, password policy, bad login, token TTL, refresh rotation/replay, logout revocation, disabled accounts, missing/invalid bearer, invalid email |
| `DeviceApiIntegrationTest` | 10 | Identifier normalization, default status, org scoping (cross-org 404), duplicate hardware within org (409) vs across orgs, MAC validation, identify case-insensitivity and cross-org denial, owner/admin-only mutation, deactivate/reactivate/soft delete, filters/pagination |
| `EventIngestIntegrationTest` | 13 | Server-side classification, device last-state/history, cross-org admin ingest, idempotency inside and across requests, inaccessible/inactive/unknown devices, null device, future timestamp clamping, pagination, invalid RSSI/empty batch/lat 91/501 events, location ingest, invalid coordinates, org isolation |
| `AdminAndSecurityIntegrationTest` | 6 | USER forbidden on admin routes, user/audit listing, settings update and public config, runtime thresholds, self-demote/self-disable guards, disable user invalidates sessions, system status |
| `ProximityClassifierTest` | 7 | Band boundaries, null input, custom thresholds, invalid ordering, 1 m reference, monotonicity, invalid exponent |
| `RealtimeWebSocketIntegrationTest` | 3 | Hello frame, live proximity frame, cross-org non-delivery, invalid-token handshake rejection |
| `RateLimitIntegrationTest` | 1 | Login limiter returns 429 `RATE_LIMITED` after the configured burst |
| **Total** | **50** | |

These are integration tests against a real Spring context and in-memory H2 in
PostgreSQL mode; they exercise security filters, validation, transactions and
JSON error shapes, not mocks.

## 3. Backend manual smoke checks

Run against the rebuilt jar (`http://localhost:8080`):

- `GET /actuator/health` → `{"status":"UP"}`; no Redis health failure when Redis
  is disabled.
- `POST /auth/login` with both demo accounts → 200 with access/refresh tokens.
- `GET /auth/me` with an invalid bearer → **401** `UNAUTHORIZED` (the first smoke
  run returned 500 because it was still hitting an older jar; the rebuilt jar is
  correct).
- USER token on `/api/v1/users` → 403; ADMIN token → 200.
- Device registration, update, deactivate, soft delete; cross-org reads → 404.
- Proximity ingest: first request accepted; replay with the same `eventId`
  counted as duplicate; device `lastRssi`/`lastProximity`/`lastSeenAt` updated;
  notification created for IMMEDIATE/VERY_CLOSE; status history written.
- WebSocket `ws://localhost:8080/ws?token=…` received `hello`,
  `notification` and `proximity.event` frames within 5 s.
- Invalid/expired token on the WebSocket handshake is rejected.

## 4. Dashboard verification

Build:

```bash
cd admin-dashboard
npm run build      # tsc -b && vite build
```

Result: success. Entry shell 355 kB (112 kB gzip); Leaflet and Recharts are
route-split into separate chunks.

Browser checks were executed with `tests/browser-verify.mjs`, which drives
headless Chrome over the DevTools Protocol with a real backend and real demo
accounts. It captures a screenshot and console/network errors per route.

| # | Check | Result |
| --- | --- | --- |
| 01 | Overview as ADMIN | Rendered stat cards, trends, recent events; 0 console errors |
| 02 | Live monitoring | Device table and realtime panels rendered; 0 console errors |
| 03 | Interactive map | OSM tiles and device markers rendered; 0 console errors |
| 04 | Devices | Registry with filters and drawer rendered |
| 05 | Analytics | Trend, distribution, availability and device charts rendered |
| 06 | Profile | Identity, session expiry, endpoints rendered |
| 07 | Admin users | Account list and role controls rendered |
| 08 | Admin audit | Filters and audit rows rendered |
| 09 | Admin settings | Threshold form and band preview rendered |
| 10 | Mobile overview (390×844) | Drawer-based responsive layout rendered |
| 11 | Mobile live (390×844) | Responsive live panels rendered |
| 12 | API unreachable | `Network.setBlockedURLs` on `/api/v1/*`; explicit "API unreachable" banner, no fabricated data |
| 13 | USER on `/admin/users` | `ERROR 403 — FORBIDDEN` page; API also enforces the role |
| 14 | Expired session | Invalid access+refresh tokens → redirected to `/login`, session cleared |

Notes on expected errors:

- Route 12 records failed `fetch` calls by design; that is the offline test.
- Route 14 records four 401 responses and one rejected WebSocket handshake before
  the redirect; those are the expected negative-path responses, not crashes.
- No other route produced a console error. The run waits 8 s for the refresh
  attempt to finish before evaluating route 14.

Artifacts:

- `documentation/screenshots/01-overview.png` … `14-expired-session.png`
- `documentation/screenshots/browser-verification.json` (URL, headings, console
  errors, failed requests and screenshot path per check)

## 5. Android verification

Command (cache-free, so stale incremental artifacts cannot mask source errors):

```bash
cd android-app
JAVA_HOME=/path/to/jdk-21 ./gradlew --no-daemon --no-build-cache \
  -Pkotlin.compiler.execution.strategy=in-process assembleDebug testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`; 36 tests, 0 failures.

| Test class | Tests | Coverage |
| --- | --- | --- |
| `RssiSmootherTest` | 11 | Priming, median spike rejection, window bound, EMA convergence, reset, even window, invalid inputs |
| `ProximityClassifierTest` | 10 | Band boundaries and custom thresholds |
| `DistanceEstimatorTest` | 7 | 1 m reference, monotonicity, 10 dB path-loss factor, clamping, formatting, invalid exponent |
| `OutboxBatchingTest` | 8 | Batch sizing, mixed kinds, duplicate acknowledgement, transport failure propagation, no-ack failure, unknown rejection ids |
| **Total** | **36** | |

APK inspection (`aapt dump badging/permissions`):

- package `com.proxilink.android`, version `1.0.0`, minSdk 26, targetSdk 36;
- `BLUETOOTH_SCAN` without `neverForLocation`, `BLUETOOTH_CONNECT`,
  fine/coarse location, notifications, foreground-service
  `connectedDevice`; legacy Bluetooth permissions capped at SDK 30.

**Not verified on-device:** no emulator/AVD or phone was available. Compose
screens, runtime permission dialogs, BLE scanning, GPS fixes, foreground-service
behaviour, Room/WorkManager execution and visual layout were therefore **not**
run. The unit tests cover pure domain logic only.

## 6. Simulator run

```bash
python tools/simulator.py --base-url http://localhost:8080 \
  --email demo@proxilink.dev --password 'ProxiLink!Demo2026' \
  --count 3 --interval 1 --batch-size 3
```

Observed output:

```
ProxiLink EVENT SIMULATOR - SIMULATION MODE
...
Signed in as demo@proxilink.dev (Acme Labs)
Using existing device Simulated ESP32 Beacon (d049d81f-…)
[0001] rssi= -61.6 smoothed=-61.89 -> VERY_CLOSE   queued=1/0
[0002] rssi= -61.0 smoothed=-61.63 -> VERY_CLOSE   queued=2/0
[0003] rssi= -63.1 smoothed=-62.06 -> VERY_CLOSE   queued=3/0
  [proximity] accepted=3 duplicates=0 rejected=0
Done. samples=3 undelivered=0
```

Events were visible through the dashboard's overview/live views. The simulator
tags every event with `metadata.simulated=true` and
`generator=tools/simulator.py`; it is not evidence of hardware behaviour.

## 7. Not executed

| Area | Why | Best available alternative |
| --- | --- | --- |
| Android UI/BLE/GPS on hardware | No emulator or device | Build + unit tests + code review; simulator exercises the backend pipeline |
| ESP32 firmware | No board or Arduino toolchain | Reference sketch + integration guide, clearly marked unverified |
| Docker Compose / PostgreSQL prod profile | Docker daemon unavailable | Compose files and deployment checklist; backend tested on H2 in PostgreSQL mode |
| Horizontal WebSocket fan-out / Redis rate limiting | Single-instance test environment | Documented as a scaling limitation |
| Load/performance testing | No representative hardware | Pagination caps, batch caps and indexes documented; not benchmarked |

## 8. Defects found and fixed during verification

1. **Stale GPS fix after sharing was disabled.** `LocationRepository` kept the
   last fix in memory when the user turned location sharing off, so subsequent
   BLE events would still carry coordinates. It now clears the cached fix on
   disable/denial, and `ScanCoordinator` requires `sharingActive` before attaching
   a fix.
2. **Permission denial still started background monitoring.** The monitoring
   permission callback enabled the service regardless of the system result. It
   now checks scan/connect/notification grants (and location for sharing) before
   enabling.
3. **`neverForLocation` conflicted with product behaviour.** Scan observations
   are paired with GPS fixes, so the assertion was inaccurate and also filtered
   beacon results. The flag was removed and the Android 12+ scan flow now
   requests fine location explicitly.
4. **Compile errors in Android sources.** An invalid expression-bodied assignment
   (`reset()`) and `List<String>` passed where `Array<String>` was required were
   fixed; the project now compiles cleanly.
5. **Android verification used stale build-cache classes.** Two unit tests saw an
   old path-loss constant and old outbox behaviour. A cache-free rebuild showed
   the source was correct; results above are from that build. This was a build
   environment issue, not a product defect, but it is why the exact command is
   documented.
6. **Stale backend jar during an early smoke test.** An old process still held
   port 8080 and returned 500 for `/auth/me` with an invalid token. The rebuilt
   jar returns 401; the old process was stopped with `taskkill`.
7. **Gradle test-executor crash on the first confirmation rerun.** The executor
   JVM exited before producing any result XML (`Connection reset by peer` in the
   Gradle message hub) while other daemons were active. An isolated test class
   passed, and the final full-suite rerun below used
   `./gradlew --no-daemon --max-workers=1 test` and produced 50 passing tests.
   No product code changed; the failure was environmental.

## 9. Reproduction

```bash
# 1. Backend
cd backend && ./gradlew bootRun

# 2. Dashboard
cd admin-dashboard && npm install && npm run dev

# 3. Synthetic data
python tools/simulator.py --base-url http://localhost:8080 \
  --email demo@proxilink.dev --password 'ProxiLink!Demo2026' --count 10

# 4. Automated checks
cd backend && ./gradlew --no-daemon --max-workers=1 test
cd android-app && ./gradlew assembleDebug testDebugUnitTest
cd admin-dashboard && npm run build
node tests/browser-verify.mjs      # requires Chrome + running backend/dashboard
```

## 10. How to read this report

Passing tests do not prove the product works on real hardware. The backend,
dashboard and pure-domain Android logic were exercised; the radio, GPS, Compose
UI and firmware were not. RSSI bands and distances remain estimates even when
every automated check passes.
