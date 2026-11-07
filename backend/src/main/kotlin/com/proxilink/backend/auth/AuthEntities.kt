package com.proxilink.backend.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "roles")
class Role(
    @Id
    var id: UUID? = null,
    @Column(nullable = false, unique = true)
    var name: String = ""
)

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(nullable = false, unique = true)
    var email: String = "",
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String = "",
    @Column(name = "display_name", nullable = false)
    var displayName: String = "",
    @Column(nullable = false)
    var organization: String = "",
    @Column(nullable = false)
    var enabled: Boolean = true,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf()
)

@Entity
@Table(name = "user_sessions")
class UserSession(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID.randomUUID(),
    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    var refreshTokenHash: String = "",
    @Column(name = "device_label")
    var deviceLabel: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now(),
    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,
    @Column(name = "last_used_at")
    var lastUsedAt: Instant? = null
)
