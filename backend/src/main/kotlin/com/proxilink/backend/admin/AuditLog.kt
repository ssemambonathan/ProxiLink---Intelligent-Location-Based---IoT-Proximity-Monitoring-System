package com.proxilink.backend.admin

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_logs")
class AuditLog(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "actor_id")
    var actorId: UUID? = null,
    @Column(nullable = false)
    var action: String = "",
    @Column(nullable = false)
    var category: String = "",
    @Column(name = "entity_type")
    var entityType: String? = null,
    @Column(name = "entity_id")
    var entityId: String? = null,
    var ip: String? = null,
    @Column(name = "user_agent")
    var userAgent: String? = null,
    var details: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
