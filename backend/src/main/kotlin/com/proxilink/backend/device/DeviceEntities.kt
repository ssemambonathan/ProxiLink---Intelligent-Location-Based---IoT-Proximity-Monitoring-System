package com.proxilink.backend.device

import com.proxilink.backend.auth.User
import com.proxilink.backend.event.ProximityLevel
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class DeviceStatus {
    ONLINE,
    OFFLINE,
    NEARBY,
    OUT_OF_RANGE,
    UNREGISTERED,
    INACTIVE
}

enum class DeviceCategory {
    ASSET,
    SENSOR,
    WEARABLE,
    INFRASTRUCTURE,
    GATEWAY,
    OTHER
}

@Entity
@Table(name = "devices")
class Device(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User? = null,
    @Column(nullable = false)
    var organization: String = "",
    @Column(nullable = false)
    var name: String = "",
    @Column(name = "device_type", nullable = false)
    var deviceType: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var category: DeviceCategory = DeviceCategory.OTHER,
    @Column(name = "hardware_id")
    var hardwareId: String? = null,
    @Column(name = "ble_mac")
    var bleMac: String? = null,
    @Column(name = "service_uuid")
    var serviceUuid: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DeviceStatus = DeviceStatus.OFFLINE,
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "last_seen_at")
    var lastSeenAt: Instant? = null,
    @Column(name = "last_rssi")
    var lastRssi: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "last_proximity")
    var lastProximity: ProximityLevel? = null,
    @Column(name = "last_latitude")
    var lastLatitude: Double? = null,
    @Column(name = "last_longitude")
    var lastLongitude: Double? = null,
    @Column(name = "last_accuracy_m")
    var lastAccuracyM: Double? = null,
    @Column(name = "last_location_at")
    var lastLocationAt: Instant? = null,
    var metadata: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

@Entity
@Table(name = "device_registrations")
class DeviceRegistration(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "device_id", nullable = false)
    var deviceId: UUID = UUID.randomUUID(),
    @Column(name = "registered_by")
    var registeredBy: UUID? = null,
    @Column(nullable = false)
    var method: String = "MANUAL",
    var notes: String? = null,
    @Column(name = "registered_at", nullable = false)
    var registeredAt: Instant = Instant.now()
)

@Entity
@Table(name = "device_status_history")
class DeviceStatusHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "device_id", nullable = false)
    var deviceId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DeviceStatus = DeviceStatus.OFFLINE,
    var reason: String? = null,
    @Column(name = "changed_at", nullable = false)
    var changedAt: Instant = Instant.now()
)

interface CategoryCount {
    val category: String
    val count: Long
}
