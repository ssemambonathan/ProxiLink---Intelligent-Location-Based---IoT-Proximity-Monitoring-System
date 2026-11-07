# ESP32 BLE beacon firmware

Reference firmware for the hardware side of ProxiLink: an ESP32 advertises a
ProxiLink service and exposes read-only GATT telemetry. The Android app discovers
it through a BLE scan and reports RSSI-based proximity; the backend stores the
resulting events.

> **Verification status:** this sketch is provided as reference firmware. It was
> **not flashed to a physical ESP32 in the development environment** and could
> not be compiled there because no Arduino/ESP32 toolchain was installed. Treat
> the pin mapping, battery divider and timing as a starting point to validate on
> your own board. See [documentation/hardware-integration.md](../documentation/hardware-integration.md)
> for the full integration guide and honest limitations.

## What it broadcasts

| Item | Value |
| --- | --- |
| Complete local name | `ProxiLink-ESP32` |
| Primary service UUID | `6e400001-b5a3-f393-e0a9-e50e24dcca9e` |
| Telemetry characteristic | `6e400003-...` read + notify (UTF-8 JSON) |
| Zone characteristic | `6e400004-...` write, accepts `ZONE=<label>` |
| Manufacturer data (scan response) | `PX` + layout `1` + fw major/minor + last 6 chars of the hardware id |

The advertisement only carries the 128-bit service UUID; the scan response
carries the name and the compact manufacturer data so both PDUs stay inside the
31-byte legacy advertising limit. The full hardware id and telemetry are
available over GATT.

## GATT telemetry

```json
{
  "hardwareId": "esp32-001",
  "firmware": "1.0.0",
  "uptimeSeconds": 421,
  "zone": "lab",
  "batteryMv": null,
  "simulated": false
}
```

`batteryMv` is `null` unless `PROXILINK_BATTERY_SENSE` is enabled and the battery
divider is wired to GPIO34. A `null` value means "not measured" - the firmware
does not fabricate sensor readings.

## Build and flash

Arduino CLI (BLE support ships with the ESP32 core, no extra libraries):

```bash
arduino-cli core install esp32:esp32
arduino-cli compile --fqbn esp32:esp32:esp32 iot-hardware/esp32-ble-beacon
arduino-cli board list
arduino-cli upload -p COM5 --fqbn esp32:esp32:esp32 iot-hardware/esp32-ble-beacon
arduino-cli monitor -p COM5 -c baudrate=115200
```

Adjust `PROXILINK_HARDWARE_ID` per device before flashing so each beacon is
unique inside the organization.

## Register the device in ProxiLink

1. Open the Android app's **Scan** screen and find `ProxiLink-ESP32`.
2. Note the MAC address shown by the scanner (Android reports the advertised
   address; some phones randomize their own address, not the beacon's).
3. Register a device with:
   - hardware id: `esp32-001` (must match the firmware)
   - BLE MAC: the address observed by the scanner
   - service UUID: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
   - category: `SENSOR`, type: `ESP32 BLE Beacon`
4. The scanner now classifies it as **recognized** and proximity events start
   flowing to the backend.

## Power notes

- `BLEDevice::setPower(ESP_PWR_LVL_P9)` uses +9 dBm; reduce to `P3` or `P0` if
  the beacon is heard too far away or local regulations require it.
- Wi-Fi is never started, so the radio only advertises BLE.
- For battery operation, disable the status LED and lower the TX power; deep
  sleep is incompatible with continuous advertising.
