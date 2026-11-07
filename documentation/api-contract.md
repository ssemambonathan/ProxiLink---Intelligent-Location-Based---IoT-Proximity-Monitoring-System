# ProxiLink API Contract v1

Base URL: `http://localhost:8080/api/v1`

All endpoints except `/auth/register`, `/auth/login`, `/auth/refresh` and `/config/public`
require `Authorization: Bearer <accessToken>`.

JSON uses ISO-8601 UTC timestamps (`2026-11-07T13:55:00Z`). IDs are UUID strings.

## Error format

```json
{
  "timestamp": "2026-11-07T13:55:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "path": "/api/v1/devices",
  "violations": { "name": "must not be blank" }
}
```

Status codes: `400` validation, `401` missing/invalid token, `403` forbidden,
`404` not found, `409` conflict (duplicate email/event handled as duplicate),
`422` business rule violation, `429` rate limited, `500` unexpected.

## Pagination

All list endpoints accept `page` (default 0), `size` (default 20, max 100) and return:

```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

## Auth

### POST /auth/register

```json
{ "email": "ada@example.com", "password": "Str0ng!Passw0rd", "displayName": "Ada", "organization": "Acme Labs" }
```

`201` → token pair (same shape as login). Password: 10–100 chars, at least one letter and one digit.
`organization` is optional; defaults to the email domain.

### POST /auth/login

```json
{ "email": "ada@example.com", "password": "Str0ng!Passw0rd" }
```

`200` →

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "opaque-64-char-token",
  "tokenType": "Bearer",
  "expiresInSeconds": 1800,
  "user": {
    "id": "uuid", "email": "ada@example.com", "displayName": "Ada",
    "organization": "Acme Labs", "roles": ["USER"], "enabled": true,
    "createdAt": "2026-11-01T10:00:00Z"
  }
}
```

### POST /auth/refresh

`{ "refreshToken": "..." }` → new token pair. Refresh tokens are single-use (rotated).
`401` if expired, revoked or unknown.

### POST /auth/logout

`{ "refreshToken": "..." }` with bearer token → `204`.

### GET /auth/me → User

## Devices

### GET /devices

Query: `page`, `size`, `q` (name/hardwareId/MAC substring), `status`
(`ONLINE|OFFLINE|NEARBY|OUT_OF_RANGE|UNREGISTERED|INACTIVE`), `category`,
`organization` (ADMIN only), `active` (default true).

### POST /devices

```json
{
  "name": "Dock Door Beacon",
  "deviceType": "beacon",
  "category": "ASSET",
  "hardwareId": "esp32-a1b2c3",
  "bleMac": "C4:DD:57:11:22:33",
  "serviceUuid": "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
  "metadata": { "floor": "1", "zone": "dock" }
}
```

Categories: `ASSET`, `SENSOR`, `WEARABLE`, `INFRASTRUCTURE`, `GATEWAY`, `OTHER`.
`201` → Device.

### GET /devices/{id} → Device
### PATCH /devices/{id}

Partial update of the same fields as create. Owner or ADMIN in the same organization only.

### POST /devices/{id}/activate → Device
### POST /devices/{id}/deactivate → Device
### DELETE /devices/{id} → `204` (soft delete: `active=false`, status `INACTIVE`)

### POST /devices/identify

```json
{ "hardwareId": "esp32-a1b2c3", "bleMac": "C4:DD:57:11:22:33", "serviceUuid": "6e400001-..." }
```

→ `{ "recognized": true, "device": { ... } }` (any one identifier is enough; searched in caller's organization).

### Device object

```json
{
  "id": "uuid", "name": "Dock Door Beacon", "deviceType": "beacon", "category": "ASSET",
  "hardwareId": "esp32-a1b2c3", "bleMac": "C4:DD:57:11:22:33",
  "serviceUuid": "6e400001-...", "ownerId": "uuid", "ownerName": "Ada",
  "organization": "Acme Labs", "status": "NEARBY", "active": true,
  "lastSeenAt": "2026-11-07T13:50:00Z", "lastRssi": -61, "lastProximity": "VERY_CLOSE",
  "lastLocation": { "latitude": 12.9716, "longitude": 77.5946, "accuracyM": 12.5, "recordedAt": "2026-11-07T13:49:50Z" },
  "createdAt": "2026-11-01T10:00:00Z", "updatedAt": "2026-11-07T13:50:00Z",
  "metadata": { "floor": "1" }
}
```

## Event ingestion (idempotent batch sync)

The Android outbox posts batches. Each event carries a client-generated `eventId` (UUID).
Re-posting the same `eventId` is safe: it is counted in `duplicates`, never stored twice.

### POST /events/proximity

```json
{
  "events": [{
    "eventId": "0b0b1f6e-8f2f-4f5e-9a63-2f5f7c1d9a11",
    "deviceId": "uuid-or-null",
    "bleMac": "C4:DD:57:11:22:33",
    "deviceName": "Dock Door Beacon",
    "rssi": -63,
    "smoothedRssi": -61.4,
    "latitude": 12.9716, "longitude": 77.5946, "accuracyM": 12.5,
    "recordedAt": "2026-11-07T13:50:00Z",
    "metadata": { "scanMode": "balanced" }
  }]
}
```

`proximityLevel` is **derived server-side** from `smoothedRssi ?? rssi` using the
configured thresholds, so clients cannot label a weak signal as immediate.

Response `200`:

```json
{ "accepted": 1, "duplicates": 0, "rejected": [] }
```

Rejections: `{ "eventId": "...", "reason": "device_not_accessible" }`.

### POST /events/location

```json
{
  "events": [{
    "eventId": "uuid", "deviceId": null,
    "latitude": 12.9716, "longitude": 77.5946, "accuracyM": 8.0,
    "speedMps": 1.2, "altitudeM": 920.0,
    "recordedAt": "2026-11-07T13:50:00Z", "metadata": {}
  }]
}
```

Same response shape.

### GET /events/proximity

Query: `deviceId`, `proximityLevel`, `from`, `to`, `page`, `size`. Newest first.

### GET /events/location

Query: `deviceId`, `from`, `to`, `page`, `size`. Newest first.

### ProximityEvent object

```json
{
  "id": "uuid", "eventId": "uuid", "deviceId": "uuid", "deviceName": "Dock Door Beacon",
  "userId": "uuid", "userDisplayName": "Ada",
  "bleMac": "C4:DD:57:11:22:33", "rssi": -63, "smoothedRssi": -61.4,
  "proximityLevel": "VERY_CLOSE", "estimatedDistanceM": 5.2,
  "latitude": 12.9716, "longitude": 77.5946, "accuracyM": 12.5,
  "recordedAt": "2026-11-07T13:50:00Z", "receivedAt": "2026-11-07T13:50:02Z",
  "metadata": { "scanMode": "balanced" }
}
```

### LocationEvent object

```json
{
  "id": "uuid", "eventId": "uuid", "deviceId": null,
  "userId": "uuid", "userDisplayName": "Ada",
  "latitude": 12.9716, "longitude": 77.5946, "accuracyM": 8.0,
  "speedMps": 1.2, "altitudeM": 920.0, "source": "GPS",
  "recordedAt": "2026-11-07T13:50:00Z", "receivedAt": "2026-11-07T13:50:02Z"
}
```

## Dashboard & analytics (ADMIN sees all; USER sees own organization)

- `GET /dashboard/overview` →
  `{ totalDevices, onlineDevices, offlineDevices, activeProximityEvents, recentLocationEvents24h,
     unreadNotifications, devicesByCategory: [{category,count}], discoveredToday }`
- `GET /dashboard/live` →
  `{ devices: [Device], recentProximity: [ProximityEvent], recentLocations: [LocationEvent], serverTime }`
- `GET /analytics/trends?days=7` →
  `{ points: [{date:"2026-11-07", proximityEvents, locationEvents, uniqueDevices}] }`
- `GET /analytics/devices?days=7` →
  `{ mostActive: [{deviceId,name,eventCount}], proximityDistribution: [{level,count}], onlineRatio: {online,offline} }`
- `GET /system/status` →
  `{ status:"UP", database:"UP", redis:"DISABLED|UP|DOWN", websocketSessions, uptimeSeconds, version }`

## Notifications

- `GET /notifications?unreadOnly=false&page&size`
- `POST /notifications/{id}/read` → `204`
- `POST /notifications/read-all` → `204`

```json
{ "id":"uuid", "type":"PROXIMITY_ALERT", "title":"Dock Door Beacon is very close",
  "body":"RSSI -61 dBm estimated 5.2 m", "read":false, "createdAt":"2026-11-07T13:50:02Z" }
```

## Administration (ADMIN only)

- `GET /users?q=&page&size`
- `PATCH /users/{id}` → `{ "enabled": false }` or `{ "roles": ["ADMIN","USER"] }`
  (cannot disable/demote yourself)
- `GET /audit-logs?action=&category=&actorId=&from=&to=&page&size`
- `GET /settings` / `PATCH /settings`

```json
{
  "proximityThresholds": { "immediate": -50, "veryClose": -65, "nearby": -80, "far": -95 },
  "locationIntervalSeconds": 30,
  "offlineTimeoutSeconds": 300,
  "dataRetentionDays": 90
}
```

Thresholds must satisfy `0 > immediate > veryClose > nearby > far >= -127`.

`GET /config/public` (public, no token required) returns
`{ proximityThresholds, locationIntervalSeconds, scanIntervalSeconds }` for the Android client.

## WebSocket

`ws://localhost:8080/ws?token=<accessToken>` (use `wss://` in production).

The token is validated during the handshake; invalid/expired tokens are rejected before upgrade.
Messages are JSON text frames:

```json
{ "type": "hello", "payload": { "sessionId": "...", "userId": "...", "serverTime": "..." } }
{ "type": "proximity.event", "payload": { ...ProximityEvent } }
{ "type": "location.event", "payload": { ...LocationEvent } }
{ "type": "device.status", "payload": { "deviceId": "...", "status": "ONLINE", "lastSeenAt": "...", "lastRssi": -61 } }
{ "type": "notification", "payload": { ...Notification } }
```

Client keepalive: `{ "type": "ping" }` → `{ "type": "pong" }`.

Fan-out is organization-scoped; ADMIN sessions receive everything. Access tokens live 30 minutes;
the client reconnects with a fresh token after `401`/close.
