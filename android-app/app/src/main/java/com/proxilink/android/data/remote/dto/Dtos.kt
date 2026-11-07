package com.proxilink.android.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String,
    val organization: String? = null,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long = 0,
    val user: UserDto? = null,
)

@Serializable
data class UserDto(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val organization: String = "",
    val roles: List<String> = emptyList(),
    val enabled: Boolean = true,
    val createdAt: String? = null,
)

@Serializable
data class LastLocationDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyM: Double? = null,
    val recordedAt: String? = null,
)

@Serializable
data class DeviceDto(
    val id: String = "",
    val name: String = "",
    val deviceType: String = "unknown",
    val category: String = "OTHER",
    val hardwareId: String = "",
    val bleMac: String? = null,
    val serviceUuid: String? = null,
    val ownerId: String? = null,
    val ownerName: String? = null,
    val organization: String? = null,
    val status: String = "OFFLINE",
    val active: Boolean = true,
    val lastSeenAt: String? = null,
    val lastRssi: Int? = null,
    val lastProximity: String? = null,
    val lastLocation: LastLocationDto? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class DevicePageDto(
    val items: List<DeviceDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class CreateDeviceRequest(
    val name: String,
    val deviceType: String,
    val category: String,
    val hardwareId: String,
    val bleMac: String? = null,
    val serviceUuid: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class UpdateDeviceRequest(
    val name: String? = null,
    val deviceType: String? = null,
    val category: String? = null,
    val bleMac: String? = null,
    val serviceUuid: String? = null,
    val metadata: Map<String, String>? = null,
)

@Serializable
data class IdentifyRequest(
    val hardwareId: String? = null,
    val bleMac: String? = null,
    val serviceUuid: String? = null,
)

@Serializable
data class IdentifyResponse(
    val recognized: Boolean = false,
    val device: DeviceDto? = null,
)

@Serializable
data class RejectedEventDto(
    val eventId: String = "",
    val reason: String = "",
)

@Serializable
data class IngestResponse(
    val accepted: Int = 0,
    val duplicates: Int = 0,
    val rejected: List<RejectedEventDto> = emptyList(),
)

@Serializable
data class ProximityEventDto(
    val id: String = "",
    val eventId: String = "",
    val deviceId: String? = null,
    val deviceName: String? = null,
    val userId: String? = null,
    val userDisplayName: String? = null,
    val bleMac: String? = null,
    val rssi: Int = -127,
    val smoothedRssi: Double? = null,
    val proximityLevel: String? = null,
    val estimatedDistanceM: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Double? = null,
    val recordedAt: String? = null,
    val receivedAt: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class LocationEventDto(
    val id: String = "",
    val eventId: String = "",
    val deviceId: String? = null,
    val userId: String? = null,
    val userDisplayName: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyM: Double? = null,
    val speedMps: Double? = null,
    val altitudeM: Double? = null,
    val source: String = "GPS",
    val recordedAt: String? = null,
    val receivedAt: String? = null,
)

@Serializable
data class ProximityEventPageDto(
    val items: List<ProximityEventDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class LocationEventPageDto(
    val items: List<LocationEventDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class NotificationDto(
    val id: String = "",
    val type: String = "SYSTEM",
    val title: String = "",
    val body: String = "",
    val read: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class NotificationPageDto(
    val items: List<NotificationDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 20,
    val totalElements: Int = 0,
    val totalPages: Int = 0,
)

@Serializable
data class CategoryCountDto(
    val category: String = "OTHER",
    val count: Int = 0,
)

@Serializable
data class DashboardOverviewDto(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val activeProximityEvents: Int = 0,
    val recentLocationEvents24h: Int = 0,
    val unreadNotifications: Int = 0,
    val devicesByCategory: List<CategoryCountDto> = emptyList(),
    val discoveredToday: Int = 0,
)

@Serializable
data class DashboardLiveDto(
    val devices: List<DeviceDto> = emptyList(),
    val recentProximity: List<ProximityEventDto> = emptyList(),
    val recentLocations: List<LocationEventDto> = emptyList(),
    val serverTime: String? = null,
)

@Serializable
data class ProximityThresholdsDto(
    val immediate: Int = -50,
    val veryClose: Int = -65,
    val nearby: Int = -80,
    val far: Int = -95,
)

@Serializable
data class PublicConfigDto(
    val proximityThresholds: ProximityThresholdsDto? = null,
    val locationIntervalSeconds: Int? = null,
    val scanIntervalSeconds: Int? = null,
)

@Serializable
data class SystemStatusDto(
    val status: String = "UNKNOWN",
    val database: String = "UNKNOWN",
    val redis: String = "DISABLED",
    val websocketSessions: Int = 0,
    val uptimeSeconds: Long = 0,
    val version: String = "unknown",
)

@Serializable
data class ApiErrorDto(
    val timestamp: String? = null,
    val status: Int = 0,
    val error: String? = null,
    val code: String? = null,
    val message: String? = null,
    val path: String? = null,
    @SerialName("violations") val violations: Map<String, String>? = null,
)

/** Client-generated outbox payloads; field names mirror the server ingestion contract. */
@Serializable
data class ProximityEventIngest(
    val eventId: String,
    val deviceId: String? = null,
    val bleMac: String? = null,
    val deviceName: String? = null,
    val rssi: Int,
    val smoothedRssi: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyM: Double? = null,
    val recordedAt: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class LocationEventIngest(
    val eventId: String,
    val deviceId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double? = null,
    val speedMps: Double? = null,
    val altitudeM: Double? = null,
    val recordedAt: String,
    val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class WsEnvelopeDto(
    val type: String = "",
    val payload: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class HelloPayloadDto(
    val sessionId: String = "",
    val userId: String = "",
    val serverTime: String? = null,
)

@Serializable
data class DeviceStatusPayloadDto(
    val deviceId: String = "",
    val status: String = "OFFLINE",
    val lastSeenAt: String? = null,
    val lastRssi: Int? = null,
)

@Serializable
data class PingFrame(val type: String = "ping")
