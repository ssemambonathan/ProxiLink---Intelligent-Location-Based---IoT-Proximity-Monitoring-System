package com.proxilink.backend.admin

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface AuditLogRepository : JpaRepository<AuditLog, UUID> {
    @Query(
        """
        select a from AuditLog a
        where (:action is null or a.action = :action)
          and (:category is null or a.category = :category)
          and (:actorId is null or a.actorId = :actorId)
          and (:fromTime is null or a.createdAt >= :fromTime)
          and (:toTime is null or a.createdAt <= :toTime)
        order by a.createdAt desc
        """
    )
    fun search(
        @Param("action") action: String?,
        @Param("category") category: String?,
        @Param("actorId") actorId: UUID?,
        @Param("fromTime") fromTime: Instant?,
        @Param("toTime") toTime: Instant?,
        pageable: Pageable
    ): Page<AuditLog>
}
