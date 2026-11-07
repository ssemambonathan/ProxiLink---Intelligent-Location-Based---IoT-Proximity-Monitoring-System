package com.proxilink.android.data.remote

import com.proxilink.android.data.remote.dto.DeviceDto
import com.proxilink.android.data.remote.dto.LocationEventDto
import com.proxilink.android.data.remote.dto.NotificationDto
import com.proxilink.android.data.remote.dto.ProximityEventDto
import com.proxilink.android.data.remote.dto.UserDto
import com.proxilink.android.domain.model.AppNotification
import com.proxilink.android.domain.model.DeviceCategory
import com.proxilink.android.domain.model.DeviceLocation
import com.proxilink.android.domain.model.DeviceStatus
import com.proxilink.android.domain.model.LocationEventRecord
import com.proxilink.android.domain.model.ProximityEvent
import com.proxilink.android.domain.model.RegisteredDevice
import com.proxilink.android.domain.model.UserProfile
import com.proxilink.android.domain.proximity.ProximityLevel
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

fun parseInstantOrNull(value: String?): Instant? =
    value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

fun Instant.toApiString(): String = formatter.format(truncatedTo(ChronoUnit.SECONDS))

fun UserDto.toDomain(): UserProfile = UserProfile(
    id = id,
    email = email,
    displayName = displayName.ifBlank { email.substringBefore('@') },
    organization = organization,
    roles = roles.ifEmpty { listOf("USER") },
    enabled = enabled,
    createdAt = parseInstantOrNull(createdAt),
)

fun DeviceDto.toDomain(): RegisteredDevice = RegisteredDevice(
    id = id,
    name = name,
    deviceType = deviceType,
    category = DeviceCategory.fromWire(category),
    hardwareId = hardwareId,
    bleMac = bleMac?.takeIf { it.isNotBlank() },
    serviceUuid = serviceUuid?.takeIf { it.isNotBlank() },
    ownerName = ownerName,
    organization = organization,
    status = DeviceStatus.fromWire(status),
    active = active,
    lastSeenAt = parseInstantOrNull(lastSeenAt),
    lastRssi = lastRssi,
    lastProximity = ProximityLevel.fromWire(lastProximity),
    lastLocation = lastLocation?.let {
        DeviceLocation(
            latitude = it.latitude,
            longitude = it.longitude,
            accuracyM = it.accuracyM,
            recordedAt = parseInstantOrNull(it.recordedAt),
        )
    },
    metadata = metadata,
)

fun ProximityEventDto.toDomain(): ProximityEvent = ProximityEvent(
    id = id.ifBlank { eventId },
    eventId = eventId,
    deviceId = deviceId,
    deviceName = deviceName,
    bleMac = bleMac,
    rssi = rssi,
    smoothedRssi = smoothedRssi,
    level = ProximityLevel.fromWire(proximityLevel),
    estimatedDistanceM = estimatedDistanceM,
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    recordedAt = parseInstantOrNull(recordedAt) ?: Instant.EPOCH,
    receivedAt = parseInstantOrNull(receivedAt),
    simulated = metadata["simulated"] == "true",
)

fun LocationEventDto.toDomain(): LocationEventRecord = LocationEventRecord(
    id = id.ifBlank { eventId },
    eventId = eventId,
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    speedMps = speedMps,
    altitudeM = altitudeM,
    source = source,
    recordedAt = parseInstantOrNull(recordedAt) ?: Instant.EPOCH,
)

fun NotificationDto.toDomain(): AppNotification = AppNotification(
    id = id,
    type = type,
    title = title,
    body = body,
    read = read,
    createdAt = parseInstantOrNull(createdAt) ?: Instant.EPOCH,
)
