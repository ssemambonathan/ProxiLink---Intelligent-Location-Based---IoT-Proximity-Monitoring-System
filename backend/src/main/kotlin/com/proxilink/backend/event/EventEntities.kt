package com.proxilink.backend.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.common.JsonMetadata
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "proximity_events")
class ProximityEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var organization: String = "",
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),
    @Column(name = "user_display_name", nullable = false)
    var userDisplayName: String = "",
    @Column(name = "device_id")
    var deviceId: UUID? = null,
    @Column(name = "device_name")
    var deviceName: String? = null,
    @Column(name = "ble_mac")
    var bleMac: String? = null,
    @Column(nullable = false)
    var rssi: Int = 0,
    @Column(name = "smoothed_rssi")
    var smoothedRssi: Double? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "proximity_level", nullable = false)
    var proximityLevel: ProximityLevel = ProximityLevel.OUT_OF_RANGE,
    @Column(name = "estimated_distance_m")
    var estimatedDistanceM: Double? = null,
    var latitude: Double? = null,
    var longitude: Double? = null,
    @Column(name = "accuracy_m")
    var accuracyM: Double? = null,
    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Instant = Instant.now(),
    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant = Instant.now(),
    var metadata: String? = null
)

@Entity
@Table(name = "location_events")
class LocationEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "event_id", nullable = false, unique = true)
    var eventId: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var organization: String = "",
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),
    @Column(name = "user_display_name", nullable = false)
    var userDisplayName: String = "",
    @Column(name = "device_id")
    var deviceId: UUID? = null,
    @Column(nullable = false)
    var latitude: Double = 0.0,
    @Column(nullable = false)
    var longitude: Double = 0.0,
    @Column(name = "accuracy_m")
    var accuracyM: Double? = null,
    @Column(name = "speed_mps")
    var speedMps: Double? = null,
    @Column(name = "altitude_m")
    var altitudeM: Double? = null,
    @Column(nullable = false)
    var source: String = "GPS",
    @Column(name = "recorded_at", nullable = false)
    var recordedAt: Instant = Instant.now(),
    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant = Instant.now(),
    var metadata: String? = null
)

data class ProximityEventInput(
    @field:NotNull val eventId: UUID,
    val deviceId: UUID? = null,
    @field:Size(max = 32) val bleMac: String? = null,
    @field:Size(max = 120) val deviceName: String? = null,
    @field:Min(-127) @field:Max(0) val rssi: Int,
    @field:DecimalMin("-127.0") @field:DecimalMax("0.0") val smoothedRssi: Double? = null,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double? = null,
    @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double? = null,
    @field:DecimalMin("0.0") val accuracyM: Double? = null,
    @field:NotNull val recordedAt: Instant,
    val metadata: Map<String, Any?>? = null
)

data class LocationEventInput(
    @field:NotNull val eventId: UUID,
    val deviceId: UUID? = null,
    @field:NotNull @field:DecimalMin("-90.0") @field:DecimalMax("90.0") val latitude: Double,
    @field:NotNull @field:DecimalMin("-180.0") @field:DecimalMax("180.0") val longitude: Double,
    @field:DecimalMin("0.0") val accuracyM: Double? = null,
    val speedMps: Double? = null,
    val altitudeM: Double? = null,
    @field:NotNull val recordedAt: Instant,
    val metadata: Map<String, Any?>? = null
)

data class ProximityBatchRequest(
    @field:Valid @field:NotEmpty @field:Size(max = 500) val events: List<ProximityEventInput>
)

data class LocationBatchRequest(
    @field:Valid @field:NotEmpty @field:Size(max = 500) val events: List<LocationEventInput>
)

data class RejectedEvent(val eventId: UUID, val reason: String)

data class BatchIngestResponse(
    val accepted: Int,
    val duplicates: Int,
    val rejected: List<RejectedEvent>
)

data class ProximityEventDto(
    val id: UUID,
    val eventId: UUID,
    val deviceId: UUID?,
    val deviceName: String?,
    val userId: UUID,
    val userDisplayName: String,
    val bleMac: String?,
    val rssi: Int,
    val smoothedRssi: Double?,
    val proximityLevel: ProximityLevel,
    val estimatedDistanceM: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Double?,
    val recordedAt: Instant,
    val receivedAt: Instant,
    val metadata: Map<String, Any?>?
)

data class LocationEventDto(
    val id: UUID,
    val eventId: UUID,
    val deviceId: UUID?,
    val userId: UUID,
    val userDisplayName: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val speedMps: Double?,
    val altitudeM: Double?,
    val source: String,
    val recordedAt: Instant,
    val receivedAt: Instant,
    val metadata: Map<String, Any?>?
)

fun ProximityEvent.toDto(mapper: ObjectMapper): ProximityEventDto = ProximityEventDto(
    id = requireNotNull(id),
    eventId = eventId,
    deviceId = deviceId,
    deviceName = deviceName,
    userId = userId,
    userDisplayName = userDisplayName,
    bleMac = bleMac,
    rssi = rssi,
    smoothedRssi = smoothedRssi,
    proximityLevel = proximityLevel,
    estimatedDistanceM = estimatedDistanceM,
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    recordedAt = recordedAt,
    receivedAt = receivedAt,
    metadata = JsonMetadata.read(mapper, metadata)
)

fun LocationEvent.toDto(mapper: ObjectMapper): LocationEventDto = LocationEventDto(
    id = requireNotNull(id),
    eventId = eventId,
    deviceId = deviceId,
    userId = userId,
    userDisplayName = userDisplayName,
    latitude = latitude,
    longitude = longitude,
    accuracyM = accuracyM,
    speedMps = speedMps,
    altitudeM = altitudeM,
    source = source,
    recordedAt = recordedAt,
    receivedAt = receivedAt,
    metadata = JsonMetadata.read(mapper, metadata)
)
