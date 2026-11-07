package com.proxilink.backend.device

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.admin.AuditService
import com.proxilink.backend.auth.UserRepository
import com.proxilink.backend.common.ApiException
import com.proxilink.backend.common.JsonMetadata
import com.proxilink.backend.common.PageResponse
import com.proxilink.backend.realtime.RealtimeService
import com.proxilink.backend.security.UserPrincipal
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class DeviceService(
    private val devices: DeviceRepository,
    private val registrations: DeviceRegistrationRepository,
    private val history: DeviceStatusHistoryRepository,
    private val users: UserRepository,
    private val audit: AuditService,
    private val realtime: RealtimeService,
    private val objectMapper: ObjectMapper
) {

    @Transactional(readOnly = true)
    fun list(
        principal: UserPrincipal,
        query: String?,
        status: DeviceStatus?,
        category: DeviceCategory?,
        organization: String?,
        active: Boolean?,
        page: Int,
        size: Int
    ): PageResponse<DeviceDto> {
        val scopedOrganization = if (principal.isAdmin) {
            organization?.trim()?.takeIf { it.isNotEmpty() }
        } else {
            principal.organization
        }
        val pageable = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, 100),
            Sort.by(Sort.Direction.DESC, "updatedAt")
        )
        val result = devices.findAll(
            DeviceSpecifications.build(scopedOrganization, query, status, category, active),
            pageable
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
    fun get(principal: UserPrincipal, id: UUID): DeviceDto {
        val device = devices.findById(id).orElse(null)
        if (device == null || !canRead(principal, device)) {
            throw ApiException.notFound("DEVICE_NOT_FOUND", "Device not found")
        }
        return device.toDto(objectMapper)
    }

    @Transactional
    fun create(principal: UserPrincipal, request: CreateDeviceRequest): DeviceDto {
        val organization = if (principal.isAdmin) {
            request.organization?.trim()?.takeIf { it.isNotEmpty() } ?: principal.organization
        } else {
            principal.organization
        }
        val hardwareId = request.hardwareId?.trim()?.takeIf { it.isNotEmpty() }
        if (hardwareId != null && devices.findByOrganizationAndHardwareId(organization, hardwareId) != null) {
            throw ApiException.conflict("HARDWARE_ID_TAKEN", "A device with this hardware id is already registered")
        }

        val now = Instant.now()
        val device = devices.save(
            Device(
                owner = users.getReferenceById(principal.id),
                organization = organization,
                name = request.name.trim(),
                deviceType = request.deviceType.trim(),
                category = request.category,
                hardwareId = hardwareId,
                bleMac = normalizeMac(request.bleMac),
                serviceUuid = normalizeUuid(request.serviceUuid),
                status = DeviceStatus.OFFLINE,
                active = true,
                metadata = JsonMetadata.write(objectMapper, request.metadata),
                createdAt = now,
                updatedAt = now
            )
        )
        val deviceId = requireNotNull(device.id)
        registrations.save(
            DeviceRegistration(
                deviceId = deviceId,
                registeredBy = principal.id,
                method = "MANUAL",
                registeredAt = now
            )
        )
        history.save(
            DeviceStatusHistory(
                deviceId = deviceId,
                status = DeviceStatus.OFFLINE,
                reason = "registered",
                changedAt = now
            )
        )
        audit.record(
            actorId = principal.id,
            action = "DEVICE_REGISTERED",
            category = "DEVICE",
            entityType = "DEVICE",
            entityId = deviceId.toString(),
            details = "name=${device.name}, organization=$organization"
        )
        return device.toDto(objectMapper)
    }

    @Transactional
    fun update(principal: UserPrincipal, id: UUID, request: UpdateDeviceRequest): DeviceDto {
        val device = devices.findById(id).orElse(null)
        if (device == null || !canRead(principal, device)) {
            throw ApiException.notFound("DEVICE_NOT_FOUND", "Device not found")
        }
        if (!canWrite(principal, device)) {
            throw ApiException.forbidden("DEVICE_FORBIDDEN", "Only the owner or an administrator can modify this device")
        }

        request.name?.let { device.name = it.trim() }
        request.deviceType?.let { device.deviceType = it.trim() }
        request.category?.let { device.category = it }
        request.hardwareId?.let { newHardwareId ->
            val trimmed = newHardwareId.trim()
            if (trimmed != device.hardwareId) {
                val existing = devices.findByOrganizationAndHardwareId(device.organization, trimmed)
                if (existing != null && existing.id != device.id) {
                    throw ApiException.conflict("HARDWARE_ID_TAKEN", "A device with this hardware id is already registered")
                }
                device.hardwareId = trimmed.ifEmpty { null }
            }
        }
        request.bleMac?.let { device.bleMac = normalizeMac(it) }
        request.serviceUuid?.let { device.serviceUuid = normalizeUuid(it) }
        request.metadata?.let { device.metadata = JsonMetadata.write(objectMapper, it) }
        device.updatedAt = Instant.now()

        audit.record(
            actorId = principal.id,
            action = "DEVICE_UPDATED",
            category = "DEVICE",
            entityType = "DEVICE",
            entityId = id.toString(),
            details = "name=${device.name}"
        )
        return device.toDto(objectMapper)
    }

    @Transactional
    fun setActive(principal: UserPrincipal, id: UUID, active: Boolean): DeviceDto {
        val device = devices.findById(id).orElse(null)
        if (device == null || !canRead(principal, device)) {
            throw ApiException.notFound("DEVICE_NOT_FOUND", "Device not found")
        }
        if (!canWrite(principal, device)) {
            throw ApiException.forbidden("DEVICE_FORBIDDEN", "Only the owner or an administrator can modify this device")
        }
        device.active = active
        device.status = if (active) DeviceStatus.OFFLINE else DeviceStatus.INACTIVE
        device.updatedAt = Instant.now()
        history.save(
            DeviceStatusHistory(
                deviceId = id,
                status = device.status,
                reason = if (active) "activated" else "deactivated",
                changedAt = Instant.now()
            )
        )
        audit.record(
            actorId = principal.id,
            action = if (active) "DEVICE_ACTIVATED" else "DEVICE_DEACTIVATED",
            category = "DEVICE",
            entityType = "DEVICE",
            entityId = id.toString()
        )
        realtime.broadcast(
            type = "device.status",
            payload = mapOf(
                "deviceId" to id,
                "status" to device.status,
                "lastSeenAt" to device.lastSeenAt,
                "lastRssi" to device.lastRssi
            ),
            organization = device.organization
        )
        return device.toDto(objectMapper)
    }

    @Transactional
    fun delete(principal: UserPrincipal, id: UUID) {
        val device = devices.findById(id).orElse(null)
        if (device == null || !canRead(principal, device)) {
            throw ApiException.notFound("DEVICE_NOT_FOUND", "Device not found")
        }
        if (!canWrite(principal, device)) {
            throw ApiException.forbidden("DEVICE_FORBIDDEN", "Only the owner or an administrator can delete this device")
        }
        device.active = false
        device.status = DeviceStatus.INACTIVE
        device.updatedAt = Instant.now()
        audit.record(
            actorId = principal.id,
            action = "DEVICE_DELETED",
            category = "DEVICE",
            entityType = "DEVICE",
            entityId = id.toString()
        )
    }

    @Transactional(readOnly = true)
    fun identify(principal: UserPrincipal, request: IdentifyDeviceRequest): IdentifyDeviceResponse {
        if (request.hardwareId.isNullOrBlank() && request.bleMac.isNullOrBlank() && request.serviceUuid.isNullOrBlank()) {
            throw ApiException.badRequest("IDENTIFIER_REQUIRED", "Provide at least one identifier")
        }
        val organization = principal.organization
        val device = request.hardwareId?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { devices.findByOrganizationAndHardwareId(organization, it) }
            ?: request.bleMac?.let { normalizeMac(it) }?.let { devices.findByOrganizationAndBleMac(organization, it) }
            ?: request.serviceUuid?.let { normalizeUuid(it) }?.let { devices.findByOrganizationAndServiceUuid(organization, it) }

        return if (device != null && device.active) {
            IdentifyDeviceResponse(recognized = true, device = device.toDto(objectMapper))
        } else {
            IdentifyDeviceResponse(recognized = false, device = null)
        }
    }

    fun canRead(principal: UserPrincipal, device: Device): Boolean =
        principal.isAdmin || device.organization == principal.organization

    fun canWrite(principal: UserPrincipal, device: Device): Boolean =
        principal.isAdmin || (device.organization == principal.organization && device.owner?.id == principal.id)

    @Transactional(readOnly = true)
    fun requireReadable(principal: UserPrincipal, id: UUID): Device {
        val device = devices.findById(id).orElse(null)
        if (device == null || !canRead(principal, device)) {
            throw ApiException.notFound("DEVICE_NOT_FOUND", "Device not found")
        }
        return device
    }

    private fun normalizeMac(value: String?): String? =
        value?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    private fun normalizeUuid(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}
