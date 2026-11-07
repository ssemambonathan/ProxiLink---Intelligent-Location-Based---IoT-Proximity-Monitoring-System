package com.proxilink.backend.device

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface DeviceRepository : JpaRepository<Device, UUID>, JpaSpecificationExecutor<Device> {
    fun findByOrganizationAndHardwareId(organization: String, hardwareId: String): Device?
    fun findByOrganizationAndBleMac(organization: String, bleMac: String): Device?
    fun findByOrganizationAndServiceUuid(organization: String, serviceUuid: String): Device?

    fun findByActiveTrueAndStatusInAndLastSeenAtBefore(
        statuses: Collection<DeviceStatus>,
        cutoff: Instant
    ): List<Device>

    @Query("select d from Device d where (:org is null or d.organization = :org) order by d.updatedAt desc")
    fun findRecent(@Param("org") org: String?, pageable: Pageable): List<Device>

    fun countByActiveTrue(): Long
    fun countByActiveTrueAndOrganization(organization: String): Long
    fun countByStatusIn(statuses: Collection<DeviceStatus>): Long
    fun countByStatusInAndOrganization(statuses: Collection<DeviceStatus>, organization: String): Long
    fun countByCreatedAtAfter(createdAt: Instant): Long
    fun countByOrganizationAndCreatedAtAfter(organization: String, createdAt: Instant): Long

    @Query(
        """
        select d.category as category, count(d) as count
        from Device d
        where d.active = true and (:org is null or d.organization = :org)
        group by d.category
        """
    )
    fun countByCategory(@Param("org") org: String?): List<CategoryCount>
}

interface DeviceRegistrationRepository : JpaRepository<DeviceRegistration, UUID>

interface DeviceStatusHistoryRepository : JpaRepository<DeviceStatusHistory, UUID> {
    fun findTop20ByDeviceIdOrderByChangedAtDesc(deviceId: UUID): List<DeviceStatusHistory>
}
