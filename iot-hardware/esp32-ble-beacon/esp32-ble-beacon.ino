/*
 * ProxiLink ESP32 BLE beacon
 * ------------------------------------------------------------------
 * Broadcasts a ProxiLink-compatible BLE advertisement and exposes a
 * read-only GATT telemetry characteristic.
 *
 * IMPORTANT
 *  - This sketch is part of the ProxiLink demonstration firmware.
 *  - It was NOT flashed to physical hardware in the development
 *    environment; treat the pin mapping and battery calibration as a
 *    starting point to validate on your own board.
 *  - The Android app discovers this device from its advertisement and
 *    estimates proximity from RSSI. RSSI is an approximation.
 *
 * Target: ESP32 DevKit (Arduino core for ESP32, BLE library bundled)
 * Board:  esp32:esp32:esp32 (ESP32 Dev Module)
 *
 * Advertisement layout (kept inside the 31-byte legacy limit)
 *  Advertising PDU     : flags + 128-bit service UUID
 *  Scan response PDU   : complete local name + manufacturer data
 *  Manufacturer data   : "PX" + layout(1) + fwMajor + fwMinor + 6-char id suffix
 *
 * The full hardware id, firmware version and telemetry are available over GATT;
 * the advertisement only carries a short suffix because of the 31-byte limit.
 *
 * GATT layout
 *  Device Information 0x180A : manufacturer / model / firmware
 *  ProxiLink service  6e400001-... :
 *      6e400003-... read + notify : JSON telemetry
 *      6e400004-... write         : "ZONE=<label>" or "SLOT=<label>" (optional)
 */

#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <esp_bt.h>

// ------------------------------------------------------------------ config
#define PROXILINK_DEVICE_NAME   "ProxiLink-ESP32"
#define PROXILINK_HARDWARE_ID    "esp32-001"          // must be unique per organization
#define PROXILINK_FW_VERSION     "1.0.0"
#define PROXILINK_ZONE           "lab"

// Enables the battery divider on GPIO34 (2:1 divider). Leave 0 when the pin is
// floating, otherwise telemetry reports meaningless millivolts.
#define PROXILINK_BATTERY_SENSE  0
#define PROXILINK_BATTERY_PIN    34

#define PROXILINK_TX_POWER        ESP_PWR_LVL_P9       // +9 dBm
#define PROXILINK_LED_PIN         2

static const char *PROXILINK_SERVICE_UUID   = "6e400001-b5a3-f393-e0a9-e50e24dcca9e";
static const char *PROXILINK_TELEMETRY_UUID = "6e400003-b5a3-f393-e0a9-e50e24dcca9e";
static const char *PROXILINK_ZONE_UUID      = "6e400004-b5a3-f393-e0a9-e50e24dcca9e";

BLECharacteristic *telemetryCharacteristic = nullptr;
BLECharacteristic *zoneCharacteristic = nullptr;
BLEAdvertising *advertising = nullptr;
volatile bool clientConnected = false;
volatile bool telemetrySubscribed = false;
String zoneLabel = PROXILINK_ZONE;
uint32_t lastTelemetryAt = 0;

static uint32_t batteryMillivolts() {
#if PROXILINK_BATTERY_SENSE
  // 12 dB attenuation covers the 0-3.3 V range; the divider halves pack voltage.
  return (uint32_t)(analogReadMilliVolts(PROXILINK_BATTERY_PIN) * 2);
#else
  return 0;  // 0 = not measured, not a fake reading
#endif
}

static String telemetryJson() {
  uint32_t battery = batteryMillivolts();
  String json = "{";
  json += "\"hardwareId\":\"" + String(PROXILINK_HARDWARE_ID) + "\",";
  json += "\"firmware\":\"" + String(PROXILINK_FW_VERSION) + "\",";
  json += "\"uptimeSeconds\":" + String(millis() / 1000) + ",";
  json += "\"zone\":\"" + zoneLabel + "\",";
  json += "\"batteryMv\":";
  json += battery > 0 ? String(battery) : "null";
  json += ",\"simulated\":false";
  json += "}";
  return json;
}

static void publishTelemetry() {
  if (telemetryCharacteristic == nullptr) return;
  String json = telemetryJson();
  telemetryCharacteristic->setValue(json.c_str());
  if (telemetrySubscribed && clientConnected) {
    telemetryCharacteristic->notify();
  }
}

static void startAdvertising() {
  BLEAdvertisementData advertisement;
  advertisement.setCompleteServices(BLEUUID(PROXILINK_SERVICE_UUID));

  // "PX" magic + layout revision + semantic version + last 6 chars of the id.
  String hardwareId = String(PROXILINK_HARDWARE_ID);
  String idSuffix = hardwareId.length() > 6 ? hardwareId.substring(hardwareId.length() - 6) : hardwareId;
  std::string manufacturer;
  manufacturer += 'P';
  manufacturer += 'X';
  manufacturer += (char)1;  // advertisement layout revision
  manufacturer += (char)1;  // firmware major
  manufacturer += (char)0;  // firmware minor
  manufacturer += idSuffix.c_str();

  BLEAdvertisementData scanResponse;
  scanResponse.setName(PROXILINK_DEVICE_NAME);
  scanResponse.setManufacturerData(manufacturer);

  advertising->setAdvertisementData(advertisement);
  advertising->setScanResponseData(scanResponse);
  advertising->setScanResponse(true);
  advertising->start();
  Serial.printf("[adv] advertising as %s (%s)\n", PROXILINK_DEVICE_NAME, PROXILINK_HARDWARE_ID);
}

class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) override {
    clientConnected = true;
    digitalWrite(PROXILINK_LED_PIN, HIGH);
    Serial.println("[ble] central connected");
  }

  void onDisconnect(BLEServer *server) override {
    clientConnected = false;
    telemetrySubscribed = false;
    digitalWrite(PROXILINK_LED_PIN, LOW);
    Serial.println("[ble] central disconnected; restarting advertising");
    delay(250);
    startAdvertising();
  }
};

class TelemetryCallbacks : public BLECharacteristicCallbacks {
  void onSubscribe(BLEServer *server, BLECharacteristic *characteristic) override {
    telemetrySubscribed = true;
    Serial.println("[ble] telemetry subscribed");
    publishTelemetry();
  }

  void onUnsubscribe(BLEServer *server, BLECharacteristic *characteristic) override {
    telemetrySubscribed = false;
  }
};

class ZoneCallbacks : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *characteristic) override {
    String value = String(characteristic->getValue().c_str());
    value.trim();
    int separator = value.indexOf('=');
    if (separator <= 0) return;
    String key = value.substring(0, separator);
    String newValue = value.substring(separator + 1);
    if (key.equalsIgnoreCase("SLOT") || key.equalsIgnoreCase("ZONE")) {
      if (newValue.length() > 0 && newValue.length() <= 32) {
        zoneLabel = newValue;
        Serial.printf("[ble] zone label updated to %s\n", zoneLabel.c_str());
        publishTelemetry();
      }
    }
  }
};

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println();
  Serial.println("ProxiLink ESP32 BLE beacon " PROXILINK_FW_VERSION);

  pinMode(PROXILINK_LED_PIN, OUTPUT);
  digitalWrite(PROXILINK_LED_PIN, LOW);
#if PROXILINK_BATTERY_SENSE
  analogReadResolution(12);  // calibrated millivolt reads use the core default attenuation
#endif

  BLEDevice::init(PROXILINK_DEVICE_NAME);
  BLEDevice::setPower(PROXILINK_TX_POWER);

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  // Device Information service: lets any GATT client identify the hardware.
  BLEService *deviceInfo = server->createService(BLEUUID((uint16_t)0x180A));
  BLECharacteristic *manufacturer = deviceInfo->createCharacteristic(
      BLEUUID((uint16_t)0x2A29), BLECharacteristic::PROPERTY_READ);
  manufacturer->setValue("ProxiLink");
  BLECharacteristic *model = deviceInfo->createCharacteristic(
      BLEUUID((uint16_t)0x2A24), BLECharacteristic::PROPERTY_READ);
  model->setValue("ESP32 BLE Beacon");
  BLECharacteristic *firmware = deviceInfo->createCharacteristic(
      BLEUUID((uint16_t)0x2A26), BLECharacteristic::PROPERTY_READ);
  firmware->setValue(PROXILINK_FW_VERSION);
  deviceInfo->start();

  BLEService *service = server->createService(BLEUUID(PROXILINK_SERVICE_UUID));

  telemetryCharacteristic = service->createCharacteristic(
      BLEUUID(PROXILINK_TELEMETRY_UUID),
      BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY);
  telemetryCharacteristic->addDescriptor(new BLE2902());
  telemetryCharacteristic->setCallbacks(new TelemetryCallbacks());
  telemetryCharacteristic->setValue(telemetryJson().c_str());

  zoneCharacteristic = service->createCharacteristic(
      BLEUUID(PROXILINK_ZONE_UUID),
      BLECharacteristic::PROPERTY_WRITE);
  zoneCharacteristic->setCallbacks(new ZoneCallbacks());
  service->start();

  advertising = BLEDevice::getAdvertising();
  startAdvertising();

  Serial.printf("[gatt] service %s ready\n", PROXILINK_SERVICE_UUID);
  publishTelemetry();
}

void loop() {
  if (millis() - lastTelemetryAt >= 5000) {
    lastTelemetryAt = millis();
    publishTelemetry();
  }
  delay(50);
}
