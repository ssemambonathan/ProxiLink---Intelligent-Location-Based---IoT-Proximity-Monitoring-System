package com.proxilink.backend.device

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.common.JsonMetadata
import com.proxilink.backend.event.ProximityLevel
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class DeviceLocationDto(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val recordedAt: Instant?
)

data class DeviceDto(
    val id: UUID,
    val name: String,
    val deviceType: String,
    val category: DeviceCategory,
    val hardwareId: String?,
    val bleMac: String?,
    val serviceUuid: String?,
    val ownerId: UUID,
    val ownerName: String,
    val organization: String,
    val status: DeviceStatus,
    val active: Boolean,
    val lastSeenAt: Instant?,
    val lastRssi: Int?,
    val lastProximity: ProximityLevel?,
    val lastLocation: DeviceLocationDto?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val metadata: Map<String, Any?>?
)

data class CreateDeviceRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:NotBlank @field:Size(max = 64) val deviceType: String,
    @field:NotNull val category: DeviceCategory,
    @field:Size(max = 128) val hardwareId: String? = null,
    @field:Pattern(
        regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$",
        message = "BLE MAC must look like AA:BB:CC:DD:EE:FF"
    ) val bleMac: String? = null,
    @field:Size(max = 64) val serviceUuid: String? = null,
    @field:Size(max = 120) val organization: String? = null,
    val metadata: Map<String, Any?>? = null
)

data class UpdateDeviceRequest(
    @field:Size(min = 1, max = 120) val name: String? = null,
    @field:Size(min = 1, max = 64) val deviceType: String? = null,
    val category: DeviceCategory? = null,
    @field:Size(max = 128) val hardwareId: String? = null,
    @field:Pattern(
        regexp = "^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$",
        message = "BLE MAC must look like AA:BB:CC:DD:EE:FF"
    ) val bleMac: String? = null,
    @field:Size(max = 64) val serviceUuid: String? = null,
    val metadata: Map<String, Any?>? = null
)

data class IdentifyDeviceRequest(
    @field:Size(max = 128) val hardwareId: String? = null,
    @field:Size(max = 32) val bleMac: String? = null,
    @field:Size(max = 64) val serviceUuid: String? = null
)

data class IdentifyDeviceResponse(
    val recognized: Boolean,
    val device: DeviceDto? = null
)

fun Device.toDto(mapper: ObjectMapper): DeviceDto = DeviceDto(
    id = requireNotNull(id),
    name = name,
    deviceType = deviceType,
    category = category,
    hardwareId = hardwareId,
    bleMac = bleMac,
    serviceUuid = serviceUuid,
    ownerId = requireNotNull(owner?.id),
    ownerName = owner?.displayName ?: "unknown",
    organization = organization,
    status = status,
    active = active,
    lastSeenAt = lastSeenAt,
    lastRssi = lastRssi,
    lastProximity = lastProximity,
    lastLocation = lastLatitude?.let { lat ->
        lastLongitude?.let { lng ->
            DeviceLocationDto(lat, lng, lastAccuracyM, lastLocationAt)
        }
    },
    createdAt = createdAt,
    updatedAt = updatedAt,
    metadata = JsonMetadata.read(mapper, metadata)
)
