package com.proxilink.backend.event

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface LevelCount {
    val level: ProximityLevel
    val count: Long
}

interface DeviceEventCount {
    val deviceId: UUID
    val count: Long
}

interface ProximityEventRepository : JpaRepository<ProximityEvent, UUID> {
    fun existsByEventId(eventId: UUID): Boolean

    @Query(
        """
        select e from ProximityEvent e
        where (:org is null or e.organization = :org)
          and (:deviceId is null or e.deviceId = :deviceId)
          and (:level is null or e.proximityLevel = :level)
          and (:fromTime is null or e.recordedAt >= :fromTime)
          and (:toTime is null or e.recordedAt <= :toTime)
        order by e.recordedAt desc
        """
    )
    fun search(
        @Param("org") org: String?,
        @Param("deviceId") deviceId: UUID?,
        @Param("level") level: ProximityLevel?,
        @Param("fromTime") fromTime: Instant?,
        @Param("toTime") toTime: Instant?,
        pageable: Pageable
    ): Page<ProximityEvent>

    fun countByRecordedAtAfter(cutoff: Instant): Long
    fun countByOrganizationAndRecordedAtAfter(organization: String, cutoff: Instant): Long

    @Query(
        """
        select count(e) from ProximityEvent e
        where e.recordedAt >= :cutoff and e.proximityLevel <> :excluded
          and (:org is null or e.organization = :org)
        """
    )
    fun countActive(
        @Param("org") org: String?,
        @Param("cutoff") cutoff: Instant,
        @Param("excluded") excluded: ProximityLevel
    ): Long

    fun findByRecordedAtAfter(cutoff: Instant): List<ProximityEvent>
    fun findByOrganizationAndRecordedAtAfter(organization: String, cutoff: Instant): List<ProximityEvent>

    @Query(
        """
        select e.proximityLevel as level, count(e) as count
        from ProximityEvent e
        where e.recordedAt >= :fromTime and (:org is null or e.organization = :org)
        group by e.proximityLevel
        """
    )
    fun countByLevel(@Param("org") org: String?, @Param("fromTime") fromTime: Instant): List<LevelCount>

    @Query(
        """
        select e.deviceId as deviceId, count(e) as count
        from ProximityEvent e
        where e.recordedAt >= :fromTime and e.deviceId is not null
          and (:org is null or e.organization = :org)
        group by e.deviceId
        order by count(e) desc
        """
    )
    fun mostActiveDevices(
        @Param("org") org: String?,
        @Param("fromTime") fromTime: Instant,
        pageable: Pageable
    ): List<DeviceEventCount>

    fun findTop50ByDeviceIdOrderByRecordedAtDesc(deviceId: UUID): List<ProximityEvent>

    @Modifying
    @Query("delete from ProximityEvent e where e.recordedAt < :cutoff")
    fun deleteOlderThan(@Param("cutoff") cutoff: Instant): Int
}

interface LocationEventRepository : JpaRepository<LocationEvent, UUID> {
    fun existsByEventId(eventId: UUID): Boolean

    @Query(
        """
        select e from LocationEvent e
        where (:org is null or e.organization = :org)
          and (:deviceId is null or e.deviceId = :deviceId)
          and (:fromTime is null or e.recordedAt >= :fromTime)
          and (:toTime is null or e.recordedAt <= :toTime)
        order by e.recordedAt desc
        """
    )
    fun search(
        @Param("org") org: String?,
        @Param("deviceId") deviceId: UUID?,
        @Param("fromTime") fromTime: Instant?,
        @Param("toTime") toTime: Instant?,
        pageable: Pageable
    ): Page<LocationEvent>

    fun countByRecordedAtAfter(cutoff: Instant): Long
    fun countByOrganizationAndRecordedAtAfter(organization: String, cutoff: Instant): Long

    fun findTop50ByOrganizationOrderByRecordedAtDesc(organization: String): List<LocationEvent>
    fun findTop50ByOrderByRecordedAtDesc(): List<LocationEvent>

    fun findByRecordedAtAfter(cutoff: Instant): List<LocationEvent>
    fun findByOrganizationAndRecordedAtAfter(organization: String, cutoff: Instant): List<LocationEvent>

    @Modifying
    @Query("delete from LocationEvent e where e.recordedAt < :cutoff")
    fun deleteOlderThan(@Param("cutoff") cutoff: Instant): Int
}
