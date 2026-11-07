package com.proxilink.android.domain.model

import com.proxilink.android.domain.proximity.ProximityLevel
import com.proxilink.android.domain.proximity.ProximityThresholds
import java.time.Instant

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Instant,
)

data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val organization: String,
    val roles: List<String>,
    val enabled: Boolean,
    val createdAt: Instant?,
) {
    val isAdmin: Boolean get() = roles.any { it.equals("ADMIN", ignoreCase = true) }
    val roleLabel: String get() = if (isAdmin) "Administrator" else "Member"
}

data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val recordedAt: Instant?,
)

enum class DeviceCategory(val label: String) {
    ASSET("Asset"),
    SENSOR("Sensor"),
    WEARABLE("Wearable"),
    INFRASTRUCTURE("Infrastructure"),
    GATEWAY("Gateway"),
    OTHER("Other"),
    ;

    companion object {
        fun fromWire(value: String?): DeviceCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}

enum class DeviceStatus(val label: String) {
    ONLINE("Online"),
    OFFLINE("Offline"),
    NEARBY("Nearby"),
    OUT_OF_RANGE("Out of range"),
    UNREGISTERED("Unregistered"),
    INACTIVE("Inactive"),
    ;

    val isPresent: Boolean get() = this == ONLINE || this == NEARBY

    companion object {
        fun fromWire(value: String?): DeviceStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OFFLINE
    }
}

data class RegisteredDevice(
    val id: String,
    val name: String,
    val deviceType: String,
    val category: DeviceCategory,
    val hardwareId: String,
    val bleMac: String?,
    val serviceUuid: String?,
    val ownerName: String?,
    val organization: String?,
    val status: DeviceStatus,
    val active: Boolean,
    val lastSeenAt: Instant?,
    val lastRssi: Int?,
    val lastProximity: ProximityLevel,
    val lastLocation: DeviceLocation?,
    val metadata: Map<String, String>,
) {
    val isDeleted: Boolean get() = !active || status == DeviceStatus.INACTIVE
}

data class ProximityEvent(
    val id: String,
    val eventId: String,
    val deviceId: String?,
    val deviceName: String?,
    val bleMac: String?,
    val rssi: Int,
    val smoothedRssi: Double?,
    val level: ProximityLevel,
    val estimatedDistanceM: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyM: Double?,
    val recordedAt: Instant,
    val receivedAt: Instant?,
    val simulated: Boolean = false,
)

data class LocationEventRecord(
    val id: String,
    val eventId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val speedMps: Double?,
    val altitudeM: Double?,
    val source: String,
    val recordedAt: Instant,
)

data class AppNotification(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: Instant,
)

data class CategoryCount(val category: DeviceCategory, val count: Int)

data class DashboardOverview(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val activeProximityEvents: Int = 0,
    val recentLocationEvents24h: Int = 0,
    val unreadNotifications: Int = 0,
    val discoveredToday: Int = 0,
    val devicesByCategory: List<CategoryCount> = emptyList(),
)

data class LiveSnapshot(
    val devices: List<RegisteredDevice> = emptyList(),
    val recentProximity: List<ProximityEvent> = emptyList(),
    val recentLocations: List<LocationEventRecord> = emptyList(),
    val serverTime: Instant? = null,
)

data class ServerConfig(
    val thresholds: ProximityThresholds = ProximityThresholds.DEFAULT,
    val locationIntervalSeconds: Int = 30,
    val scanIntervalSeconds: Int = 15,
    val remoteSynced: Boolean = false,
)

data class UserFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double?,
    val speedMps: Double?,
    val altitudeM: Double?,
    val recordedAt: Instant,
    val source: String = "GPS",
)
