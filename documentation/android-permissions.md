# ProxiLink — Android permissions guide

Target: `minSdk 26`, `compileSdk 36`, `targetSdk 36`. Permission handling is
centralized in `util/PermissionUtils.kt`. The app always asks at runtime, explains
why, and degrades gracefully when a permission is missing. It never tries to
bypass Android privacy restrictions.

## 1. Declared permissions

| Permission | Why | Max SDK |
| --- | --- | --- |
| `INTERNET` | REST and WebSocket calls | — |
| `ACCESS_NETWORK_STATE` | Connectivity checks before syncing | — |
| `BLUETOOTH` | Legacy discovery APIs on Android 11 and below | 30 |
| `BLUETOOTH_ADMIN` | Legacy scan control on Android 11 and below | 30 |
| `BLUETOOTH_SCAN` | BLE scanning on Android 12+ | — |
| `BLUETOOTH_CONNECT` | Reading device names/addresses and connecting on Android 12+ | — |
| `ACCESS_FINE_LOCATION` | GPS fixes and the scan requirement on Android 11 and below | — |
| `ACCESS_COARSE_LOCATION` | Approximate fallback when fine location is denied | — |
| `POST_NOTIFICATIONS` | Monitoring foreground-service notification on Android 13+ | — |
| `FOREGROUND_SERVICE` | Long-running BLE monitoring service | — |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Required foreground-service type on Android 14+ | — |

Optional hardware features (`bluetooth_le`, `location.gps`) are declared
`required="false"` so the app installs on devices without them and shows an
explicit "not supported" state instead of crashing.

`BLUETOOTH_SCAN` is deliberately **not** declared with `neverForLocation`. The app
pairs scan observations with GPS fixes from the separate fused provider when the
user enables location sharing, so asserting that scan results are never used in a
location context would be inaccurate. It also keeps iBeacon-style advertisements
in the scan results instead of having Android filter them. On Android 12+ the
system therefore requires `ACCESS_FINE_LOCATION` alongside `BLUETOOTH_SCAN`; the
app never starts scanning until both the scan and location permissions needed for
the current flow are granted. Position is always taken from the fused provider,
never inferred from RSSI. Review Google Play's location policy before publishing.

## 2. Runtime request matrix

| Android version | Scan flow requests | Location flow requests |
| --- | --- | --- |
| 13+ (API 33+) | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`, `POST_NOTIFICATIONS` | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` |
| 12 (API 31–32) | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION` | fine + coarse |
| 8–11 (API 26–30) | `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` (legacy BLE scan requirement) | fine + coarse |

`POST_NOTIFICATIONS` is requested with scanning because the monitoring service
must show a persistent notification. If it is denied, scanning still works in the
foreground but background monitoring is disabled and the UI explains why.

## 3. Permission states and behaviour

| State | Behaviour |
| --- | --- |
| Not requested | Screen shows a rationale card with a **Grant permissions** button |
| Granted | Scan/location starts immediately |
| Denied once | Rationale remains, button requests again |
| Denied permanently ("Don't ask again") | UI links to app settings; scanning stays blocked |
| Revoked while running | `SecurityException` is caught, the scan flow closes with `PERMISSION_MISSING`, the service stops cleanly |
| Bluetooth off | Explicit `BLUETOOTH_DISABLED` state with an enable prompt (no silent failure) |
| No BLE hardware | `BLUETOOTH_UNAVAILABLE` state; scanning disabled |
| Location services off | Location screen offers system settings; BLE-only monitoring remains available |
| Notification denied | Foreground scanning works; background monitoring is not offered |

## 4. Privacy behaviour

- BLE scanning and location sharing are independent switches. Turning location
  sharing off stops location capture and uploads but does not stop BLE proximity
  events (which may still include coordinates only if a fix was already attached
  while sharing was on).
- The monitoring service publishes a persistent notification while it runs. The
  user can stop it from the notification or from the app.
- Scans are stopped when the user leaves the scanning screen unless continuous
  monitoring was explicitly started.
- MAC addresses are displayed only when Android provides them. Android 12+ may
  return `02:00:00:00:00:00` for privacy; the app shows the identifier as
  unavailable rather than inventing one.
- No permission is requested at app start without context; each request is tied
  to a user action that needs it.

## 5. Foreground service

- Service: `service/BleMonitoringService`, type `connectedDevice`, `exported=false`.
- Started only after scan permissions are granted and the user enables monitoring.
- Shows device count, proximity status and a stop action.
- `stopWithTask=false` so monitoring survives task removal; the persistent
  notification makes this visible. Stopping from the notification stops the
  service and scanning.
- Android 14+ requires `FOREGROUND_SERVICE_CONNECTED_DEVICE`, which is declared.
  The service does not use `ACCESS_BACKGROUND_LOCATION`; it does not need
  background location because monitoring is foreground-visible.

## 6. Testing permissions

Recommended manual matrix (not executed in this environment because no emulator
or device was available):

1. Fresh install on API 34: deny notifications → scanning works, background
   monitoring is refused with an explanation.
2. Deny Bluetooth scan → rationale shown, no crash, no scan started.
3. Grant scan, disable Bluetooth → "Bluetooth is off" state with settings action.
4. Revoke Bluetooth permission from system settings while scanning → scan stops,
   service notification disappears.
5. Deny location, allow Bluetooth → proximity works, map/history show an explicit
   "location unavailable" state.
6. Grant coarse location only → location events carry reduced accuracy; UI labels it.
7. Enable location sharing, then disable it → no new location events are uploaded.

`PermissionUtils` is unit-testable for the version matrix; device tests require
the instrumentation runner declared in `app/build.gradle.kts`.
