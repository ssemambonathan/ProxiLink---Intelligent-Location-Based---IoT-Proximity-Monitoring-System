package com.proxilink.backend.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.admin.SettingsService
import com.proxilink.backend.common.ApiException
import com.proxilink.backend.common.JsonMetadata
import com.proxilink.backend.common.PageResponse
import com.proxilink.backend.device.Device
import com.proxilink.backend.device.DeviceRepository
import com.proxilink.backend.device.DeviceService
import com.proxilink.backend.device.DeviceStatus
import com.proxilink.backend.device.DeviceStatusHistory
import com.proxilink.backend.device.DeviceStatusHistoryRepository
import com.proxilink.backend.notification.NotificationService
import com.proxilink.backend.realtime.RealtimeService
import com.proxilink.backend.security.UserPrincipal
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Locale
import java.util.UUID

@Service
class EventService(
    private val proximityEvents: ProximityEventRepository,
    private val locationEvents: LocationEventRepository,
    private val devices: DeviceRepository,
    private val history: DeviceStatusHistoryRepository,
    private val deviceService: DeviceService,
    private val notifications: NotificationService,
    private val settings: SettingsService,
    private val realtime: RealtimeService,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun ingestProximity(principal: UserPrincipal, request: ProximityBatchRequest): BatchIngestResponse {
        val thresholds = settings.current().proximityThresholds
        val classifier = ProximityClassifier(thresholds)
        val now = Instant.now()
        val rejected = mutableListOf<RejectedEvent>()
        val seenInBatch = mutableSetOf<UUID>()
        var accepted = 0
        var duplicates = 0

        for (input in request.events) {
            if (!seenInBatch.add(input.eventId) || proximityEvents.existsByEventId(input.eventId)) {
                duplicates++
                continue
            }

            val device = input.deviceId?.let { devices.findById(it).orElse(null) }
            if (input.deviceId != null) {
                if (device == null || !deviceService.canRead(principal, device)) {
                    rejected += RejectedEvent(input.eventId, "device_not_accessible")
                    continue
                }
                if (!device.active) {
                    rejected += RejectedEvent(input.eventId, "device_inactive")
                    continue
                }
            }

            val recordedAt = input.recordedAt.takeIf { !it.isAfter(now.plusSeconds(300)) } ?: now
            val smoothed = (input.smoothedRssi ?: input.rssi.toDouble()).coerceIn(-127.0, 0.0)
            val level = classifier.classify(smoothed)
            val distance = if (level == ProximityLevel.OUT_OF_RANGE) {
                null
            } else {
                classifier.estimateDistanceMeters(smoothed)
            }
            val organization = device?.organization ?: principal.organization

            val event = proximityEvents.save(
                ProximityEvent(
                    eventId = input.eventId,
                    organization = organization,
                    userId = principal.id,
                    userDisplayName = principal.displayName,
                    deviceId = device?.id,
                    deviceName = device?.name ?: input.deviceName?.trim()?.takeIf { it.isNotEmpty() },
                    bleMac = input.bleMac?.trim()?.uppercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: device?.bleMac,
                    rssi = input.rssi,
                    smoothedRssi = smoothed,
                    proximityLevel = level,
                    estimatedDistanceM = distance,
                    latitude = input.latitude,
                    longitude = input.longitude,
                    accuracyM = input.accuracyM,
                    recordedAt = recordedAt,
                    receivedAt = now,
                    metadata = JsonMetadata.write(objectMapper, input.metadata)
                )
            )

            if (device != null) {
                applyProximityToDevice(device, input, level, distance, recordedAt, now)
            }
            realtime.broadcast("proximity.event", event.toDto(objectMapper), organization)
            accepted++
        }
        return BatchIngestResponse(accepted, duplicates, rejected)
    }

    @Transactional
    fun ingestLocation(principal: UserPrincipal, request: LocationBatchRequest): BatchIngestResponse {
        val now = Instant.now()
        val rejected = mutableListOf<RejectedEvent>()
        val seenInBatch = mutableSetOf<UUID>()
        var accepted = 0
        var duplicates = 0

        for (input in request.events) {
            if (!seenInBatch.add(input.eventId) || locationEvents.existsByEventId(input.eventId)) {
                duplicates++
                continue
            }
            val device = input.deviceId?.let { devices.findById(it).orElse(null) }
            if (input.deviceId != null) {
                if (device == null || !deviceService.canRead(principal, device)) {
                    rejected += RejectedEvent(input.eventId, "device_not_accessible")
                    continue
                }
                if (!device.active) {
                    rejected += RejectedEvent(input.eventId, "device_inactive")
                    continue
                }
            }

            val recordedAt = input.recordedAt.takeIf { !it.isAfter(now.plusSeconds(300)) } ?: now
            val organization = device?.organization ?: principal.organization
            val event = locationEvents.save(
                LocationEvent(
                    eventId = input.eventId,
                    organization = organization,
                    userId = principal.id,
                    userDisplayName = principal.displayName,
                    deviceId = device?.id,
                    latitude = input.latitude,
                    longitude = input.longitude,
                    accuracyM = input.accuracyM,
                    speedMps = input.speedMps,
                    altitudeM = input.altitudeM,
                    source = "GPS",
                    recordedAt = recordedAt,
                    receivedAt = now,
                    metadata = JsonMetadata.write(objectMapper, input.metadata)
                )
            )

            if (device != null && device.active) {
                device.lastLatitude = input.latitude
                device.lastLongitude = input.longitude
                device.lastAccuracyM = input.accuracyM
                device.lastLocationAt = recordedAt
                device.updatedAt = now
            }
            realtime.broadcast("location.event", event.toDto(objectMapper), organization)
            accepted++
        }
        return BatchIngestResponse(accepted, duplicates, rejected)
    }

    @Transactional(readOnly = true)
    fun searchProximity(
        principal: UserPrincipal,
        deviceId: UUID?,
        level: ProximityLevel?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int
    ): PageResponse<ProximityEventDto> {
        val org = if (principal.isAdmin) null else principal.organization
        val result = proximityEvents.search(
            org,
            deviceId,
            level,
            from,
            to,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        )
        return PageResponse(
            items = result.content.map { it.toDto(objectMapper) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    @Transactional(readOnly = true)
    fun searchLocation(
        principal: UserPrincipal,
        deviceId: UUID?,
        from: Instant?,
        to: Instant?,
        page: Int,
        size: Int
    ): PageResponse<LocationEventDto> {
        val org = if (principal.isAdmin) null else principal.organization
        val result = locationEvents.search(
            org,
            deviceId,
            from,
            to,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        )
        return PageResponse(
            items = result.content.map { it.toDto(objectMapper) },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }

    private fun applyProximityToDevice(
        device: Device,
        input: ProximityEventInput,
        level: ProximityLevel,
        distanceMeters: Double?,
        recordedAt: Instant,
        now: Instant
    ) {
        val previousLevel = device.lastProximity
        val previousStatus = device.status

        device.lastSeenAt = recordedAt
        device.lastRssi = input.rssi
        device.lastProximity = level
        device.updatedAt = now
        if (input.latitude != null && input.longitude != null) {
            device.lastLatitude = input.latitude
            device.lastLongitude = input.longitude
            device.lastAccuracyM = input.accuracyM
            device.lastLocationAt = recordedAt
        }

        val newStatus = when (level) {
            ProximityLevel.IMMEDIATE, ProximityLevel.VERY_CLOSE, ProximityLevel.NEARBY -> DeviceStatus.NEARBY
            ProximityLevel.FAR, ProximityLevel.OUT_OF_RANGE -> DeviceStatus.ONLINE
        }
        if (newStatus != previousStatus) {
            device.status = newStatus
            history.save(
                DeviceStatusHistory(
                    deviceId = requireNotNull(device.id),
                    status = newStatus,
                    reason = "proximity:$level",
                    changedAt = now
                )
            )
            realtime.broadcast(
                type = "device.status",
                payload = mapOf(
                    "deviceId" to device.id,
                    "status" to newStatus,
                    "lastSeenAt" to recordedAt,
                    "lastRssi" to input.rssi
                ),
                organization = device.organization
            )
        }

        val near = setOf(ProximityLevel.IMMEDIATE, ProximityLevel.VERY_CLOSE, ProximityLevel.NEARBY)
        val far = setOf(ProximityLevel.FAR, ProximityLevel.OUT_OF_RANGE)
        val crossedCloser = level in near && (previousLevel == null || previousLevel in far)
        if (crossedCloser) {
            val distanceLabel = distanceMeters?.let { "estimated %.1f m".format(it) } ?: "distance unavailable"
            notifications.create(
                userId = device.owner?.id,
                type = "PROXIMITY_ALERT",
                title = "${device.name} is ${level.name.lowercase(Locale.ROOT).replace('_', ' ')}",
                body = "RSSI ${input.rssi} dBm, $distanceLabel. Proximity is an estimate.",
                metadata = mapOf("deviceId" to device.id, "level" to level.name)
            )
        }
    }
}
