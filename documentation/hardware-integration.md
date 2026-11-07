# ProxiLink — hardware integration guide

This guide connects the physical (or simulated) beacon side of ProxiLink to the
Android scanner and the backend. It is written against the firmware in
`iot-hardware/esp32-ble-beacon/` and the Android scanner in
`android-app/app/src/main/java/com/proxilink/android/data/ble/`.

> **Honest status.** No ESP32 board, programmer or Arduino/ESP32 toolchain was
> available in the development environment. The sketch was **not compiled and
> not flashed**, and no RSSI values in this repository come from physical
> hardware. The simulator in `tools/simulator.py` is the only end-to-end event
> source that was actually executed, and its events are tagged
> `metadata.simulated=true`.

## 1. Integration path

```
ESP32 advertising (BLE)
        │  ADV_IND + SCAN_RSP
        ▼
AndroidBleScanner (callbackFlow, BLUETOOTH_SCAN + FINE_LOCATION)
        │  RawBleObservation(bleMac, name, rssi, serviceUuids)
        ▼
ScanCoordinator ──► RssiSmoother (median-5 + EMA α=0.3)
        │            ProximityClassifier (configurable dBm bands)
        │            DistanceEstimator (log-distance path loss, an estimate)
        ▼
Room outbox (client event UUID) ──► batched REST ──► backend ──► dashboard
```

The firmware does not send data to the backend itself. It only advertises and
exposes GATT telemetry; the phone is the gateway. This keeps Wi-Fi/cellular
credentials off the beacon and lets the backend validate every event.

## 2. Beacon configuration

| Setting | Default | Notes |
| --- | --- | --- |
| Local name | `ProxiLink-ESP32` | Shown as the advertised name |
| Hardware id | `esp32-001` | Must be unique per organization; change per board |
| Service UUID | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` | Matched by `DeviceService.identify` |
| TX power | `ESP_PWR_LVL_P9` (+9 dBm) | Lower it for shorter range or local rules |
| Advertising interval | firmware constant | Longer interval saves power but adds scan latency |
| Battery sense | disabled | Enabling it without a divider reports nothing useful |

The advertisement fits the 31-byte legacy limit: flags + 128-bit service UUID in
`ADV_IND`, local name + 6-byte manufacturer data in `SCAN_RSP`. Full identity and
telemetry are available over GATT.

### GATT

| Characteristic | UUID suffix | Properties | Payload |
| --- | --- | --- | --- |
| Device Information | `0x180A` service | read | Manufacturer, model, firmware |
| Telemetry | `6e400003-…` | read, notify | UTF-8 JSON, see below |
| Zone | `6e400004-…` | write | `ZONE=<label>` or `SLOT=<label>` |

```json
{"hardwareId":"esp32-001","firmware":"1.0.0","uptimeSeconds":421,"zone":"lab","batteryMv":null,"simulated":false}
```

`batteryMv` is `null` when battery sensing is compiled out. The firmware never
invents a voltage.

## 3. Build and flash

```bash
arduino-cli core install esp32:esp32
arduino-cli compile --fqbn esp32:esp32:esp32 iot-hardware/esp32-ble-beacon
arduino-cli board list
arduino-cli upload -p COM5 --fqbn esp32:esp32:esp32 iot-hardware/esp32-ble-beacon
arduino-cli monitor -p COM5 -c baudrate=115200
```

Change `PROXILINK_HARDWARE_ID` before flashing each board. Then verify the
advertisement with any BLE scanner app: the name and the service UUID should be
visible without connecting.

## 4. Register the beacon

Option A — dashboard: **Devices → Register device**, category `SENSOR`, type
`ESP32 BLE Beacon`, hardware id `esp32-001`, BLE MAC observed by the phone,
service UUID `6e400001-…`.

Option B — API:

```bash
curl -X POST http://localhost:8080/api/v1/devices \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"Lab beacon","deviceType":"ESP32 BLE Beacon","category":"SENSOR",
       "hardwareId":"esp32-001","bleMac":"AA:BB:CC:DD:EE:FF",
       "serviceUuid":"6e400001-b5a3-f393-e0a9-e50e24dcca9e"}'
```

Registration is scoped to the signed-in user's organization. Hardware ids are
unique per organization but may repeat across organizations.

## 5. Run the scanner

1. Grant Bluetooth scan/connect, fine location and notification permissions in
   the Android app. The app explains each request and does not scan before the
   user starts monitoring.
2. Open **Scan** or enable **Continuous monitoring**. A foreground service with a
   persistent stop action runs while monitoring is on.
3. The scanner identifies the beacon through `POST /api/v1/devices/identify`
   (hardware id, MAC or service UUID). Recognized devices show their registered
   name; unknown devices remain visible as unregistered.
4. Proximity events are written to the Room outbox and uploaded in batches. They
   are tagged with the scan mode and whether a location fix was attached.

RSSI bands are estimates, not distances:

| Band | Default threshold |
| --- | --- |
| IMMEDIATE | ≥ −50 dBm |
| VERY_CLOSE | ≥ −65 dBm |
| NEARBY | ≥ −80 dBm |
| FAR | ≥ −95 dBm |
| OUT_OF_RANGE | < −95 dBm |

Administrators can change thresholds in **Settings**; the Android app refreshes
them from `/api/v1/config/public`.

## 6. Verify end to end

1. Backend health: `GET /actuator/health` → `UP`.
2. Scan and confirm the beacon appears with a stable name.
3. Confirm a `proximity.event` frame on the dashboard **Live** page (WebSocket).
4. Confirm the event in **Analytics** and, if location sharing is on, in **Map**.
5. Inspect the stored event: `metadata.scanMode` should be `low_latency`, and
   `metadata.simulated` should be `false` for real hardware.
6. Move the beacon away and watch the band change; RSSI is noisy, so allow the
   smoother several samples before judging.

## 7. Troubleshooting

| Symptom | Likely cause | Action |
| --- | --- | --- |
| No scan results | Bluetooth off, permission denied, or no BLE radio | Check the in-app permission cards and system settings |
| Beacon visible but never recognized | MAC/UUID/hardware id mismatch or different organization | Re-check registration values; `identify` is org-scoped |
| RSSI jumps wildly | Multipath, body blocking, Wi-Fi interference | Keep the phone and beacon still; check smoothed RSSI, not a single sample |
| Events missing in dashboard | Outbox sync blocked (no network/auth) | Open **Sync**/network status; events retry with backoff |
| Location never attached | Location sharing off or permission denied | Enable sharing; proximity events then include the last fix |
| Beacon disappears after 10 s | No advertisements received | Check power, range and advertising interval |

## 8. Limitations

- No hardware was available, so the firmware, pin mapping, battery divider and
  actual range are unverified. Compile and flash before claiming hardware
  results.
- BLE MAC addresses may be randomized by the **phone**, not the beacon; the
  scanner reports what Android exposes, and identification falls back to the
  service UUID.
- RSSI is affected by orientation, obstacles and TX power. The log-distance
  estimate uses `txPower = −59 dBm` and `n = 2.0` and is labelled an estimate
  everywhere.
- The firmware does not implement OTA updates, secure pairing or deep sleep.
- Continuous low-latency scanning costs battery; the foreground notification and
  stop action make this visible to the user.
