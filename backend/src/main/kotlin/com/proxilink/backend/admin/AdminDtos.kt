package com.proxilink.backend.admin

import com.proxilink.backend.event.ProximityThresholds
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "system_settings")
class SystemSettings(
    @Id
    @Column(name = "setting_key")
    var key: String = "",
    @Column(name = "setting_value", nullable = false)
    var value: String = "",
    @Column(name = "updated_by")
    var updatedBy: UUID? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

data class SettingsDto(
    val proximityThresholds: ProximityThresholds,
    val locationIntervalSeconds: Int,
    val scanIntervalSeconds: Int,
    val offlineTimeoutSeconds: Int,
    val dataRetentionDays: Int
)

data class UpdateSettingsRequest(
    val proximityThresholds: ProximityThresholds? = null,
    @field:Min(5) @field:Max(3600) val locationIntervalSeconds: Int? = null,
    @field:Min(5) @field:Max(300) val scanIntervalSeconds: Int? = null,
    @field:Min(30) @field:Max(86400) val offlineTimeoutSeconds: Int? = null,
    @field:Min(1) @field:Max(3650) val dataRetentionDays: Int? = null
)

data class PublicConfigDto(
    val proximityThresholds: ProximityThresholds,
    val locationIntervalSeconds: Int,
    val scanIntervalSeconds: Int
)

data class UserUpdateRequest(
    val enabled: Boolean? = null,
    val roles: List<String>? = null
)

data class AuditLogDto(
    val id: UUID,
    val actorId: UUID?,
    val action: String,
    val category: String,
    val entityType: String?,
    val entityId: String?,
    val ip: String?,
    val userAgent: String?,
    val details: String?,
    val createdAt: Instant
)

data class SystemStatusDto(
    val status: String,
    val database: String,
    val redis: String,
    val websocketSessions: Int,
    val uptimeSeconds: Long,
    val version: String
)

fun AuditLog.toDto(): AuditLogDto = AuditLogDto(
    id = requireNotNull(id),
    actorId = actorId,
    action = action,
    category = category,
    entityType = entityType,
    entityId = entityId,
    ip = ip,
    userAgent = userAgent,
    details = details,
    createdAt = createdAt
)
