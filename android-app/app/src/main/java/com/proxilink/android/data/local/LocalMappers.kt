package com.proxilink.android.data.local

import com.proxilink.android.domain.model.DeviceCategory
import com.proxilink.android.domain.model.DeviceLocation
import com.proxilink.android.domain.model.DeviceStatus
import com.proxilink.android.domain.model.RegisteredDevice
import com.proxilink.android.domain.outbox.OutboxKind
import com.proxilink.android.domain.outbox.OutboxRecord
import com.proxilink.android.domain.outbox.OutboxState
import com.proxilink.android.domain.proximity.ProximityLevel
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val metadataSerializer = MapSerializer(String.serializer(), String.serializer())

fun OutboxEventEntity.toRecord(): OutboxRecord = OutboxRecord(
    eventId = eventId,
    kind = runCatching { OutboxKind.valueOf(kind) }.getOrDefault(OutboxKind.PROXIMITY),
    payloadJson = payloadJson,
    recordedAt = Instant.ofEpochMilli(recordedAtEpochMs),
    state = runCatching { OutboxState.valueOf(state) }.getOrDefault(OutboxState.PENDING),
    attempts = attempts,
    rejectionReason = rejectionReason,
)

fun RegisteredDevice.toEntity(now: Instant): CachedDeviceEntity = CachedDeviceEntity(
    id = id,
    name = name,
    deviceType = deviceType,
    category = category.name,
    hardwareId = hardwareId,
    bleMac = bleMac,
    serviceUuid = serviceUuid,
    ownerName = ownerName,
    organization = organization,
    status = status.name,
    active = active,
    lastSeenAtEpochMs = lastSeenAt?.toEpochMilli(),
    lastRssi = lastRssi,
    lastProximity = lastProximity.name,
    lastLatitude = lastLocation?.latitude,
    lastLongitude = lastLocation?.longitude,
    lastAccuracyM = lastLocation?.accuracyM,
    lastLocationAtEpochMs = lastLocation?.recordedAt?.toEpochMilli(),
    metadataJson = json.encodeToString(metadataSerializer, metadata),
    cachedAtEpochMs = now.toEpochMilli(),
)

fun CachedDeviceEntity.toDomain(): RegisteredDevice = RegisteredDevice(
    id = id,
    name = name,
    deviceType = deviceType,
    category = DeviceCategory.fromWire(category),
    hardwareId = hardwareId,
    bleMac = bleMac,
    serviceUuid = serviceUuid,
    ownerName = ownerName,
    organization = organization,
    status = DeviceStatus.fromWire(status),
    active = active,
    lastSeenAt = lastSeenAtEpochMs?.let { Instant.ofEpochMilli(it) },
    lastRssi = lastRssi,
    lastProximity = ProximityLevel.fromWire(lastProximity),
    lastLocation = lastLatitude?.let { lat ->
        DeviceLocation(
            latitude = lat,
            longitude = lastLongitude ?: 0.0,
            accuracyM = lastAccuracyM,
            recordedAt = lastLocationAtEpochMs?.let { Instant.ofEpochMilli(it) },
        )
    },
    metadata = runCatching { json.decodeFromString(metadataSerializer, metadataJson) }
        .getOrDefault(emptyMap()),
)
