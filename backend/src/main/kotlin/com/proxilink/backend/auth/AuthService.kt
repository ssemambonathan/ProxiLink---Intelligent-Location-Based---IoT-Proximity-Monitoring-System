package com.proxilink.backend.auth

import com.proxilink.backend.admin.AuditService
import com.proxilink.backend.common.ApiException
import com.proxilink.backend.config.AppProperties
import com.proxilink.backend.security.JwtService
import com.proxilink.backend.security.UserPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class AuthService(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val sessions: UserSessionRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditService,
    private val props: AppProperties
) {
    private val random = SecureRandom()

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "An account with this email already exists")
        }
        validatePassword(request.password)
        val organization = request.organization?.trim()?.takeIf { it.isNotEmpty() }
            ?: email.substringAfter("@").ifBlank { "Personal" }
        val userRole = roles.findByName("USER")
            ?: throw IllegalStateException("USER role is missing; check Flyway seed data")

        val now = Instant.now()
        val user = users.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(request.password),
                displayName = request.displayName.trim(),
                organization = organization,
                roles = mutableSetOf(userRole),
                createdAt = now,
                updatedAt = now
            )
        )

        audit.record(
            actorId = user.id,
            action = "REGISTER",
            category = "AUTH",
            entityType = "USER",
            entityId = user.id.toString(),
            details = "organization=$organization"
        )
        return issueTokens(user, "registration")
    }

    fun login(request: LoginRequest): AuthResponse {
        val email = request.email.trim().lowercase()
        val user = users.findByEmailIgnoreCase(email).orElse(null)
        if (user == null || !passwordEncoder.matches(request.password, user.passwordHash)) {
            audit.record(
                actorId = null,
                action = "LOGIN_FAILURE",
                category = "SECURITY",
                entityType = "USER",
                details = "email=$email"
            )
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password")
        }
        if (!user.enabled) {
            audit.record(
                actorId = user.id,
                action = "LOGIN_FAILURE",
                category = "SECURITY",
                entityType = "USER",
                entityId = user.id.toString(),
                details = "account disabled"
            )
            throw ApiException.forbidden("ACCOUNT_DISABLED", "This account is disabled")
        }

        val now = Instant.now()
        users.updateLastLogin(requireNotNull(user.id), now)
        audit.record(
            actorId = user.id,
            action = "LOGIN_SUCCESS",
            category = "SECURITY",
            entityType = "USER",
            entityId = user.id.toString(),
            details = "email=$email"
        )
        user.lastLoginAt = now
        return issueTokens(user, request.deviceLabel)
    }

    @Transactional
    fun refresh(refreshToken: String): AuthResponse {
        val now = Instant.now()
        val session = sessions.findByRefreshTokenHash(hashToken(refreshToken))
            .orElseThrow { ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired") }
        if (session.revokedAt != null || session.expiresAt.isBefore(now)) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired")
        }
        val user = users.findById(session.userId)
            .orElseThrow { ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session owner no longer exists") }
        if (!user.enabled) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "This account is disabled")
        }

        session.revokedAt = now
        session.lastUsedAt = now
        sessions.save(session)

        audit.record(
            actorId = user.id,
            action = "TOKEN_REFRESH",
            category = "SECURITY",
            entityType = "SESSION",
            entityId = session.id.toString()
        )
        return issueTokens(user, session.deviceLabel)
    }

    @Transactional
    fun logout(refreshToken: String, principal: UserPrincipal) {
        val session = sessions.findByRefreshTokenHash(hashToken(refreshToken)).orElse(null)
        if (session == null || session.userId != principal.id) {
            return
        }
        if (session.revokedAt == null) {
            session.revokedAt = Instant.now()
            sessions.save(session)
            audit.record(
                actorId = principal.id,
                action = "LOGOUT",
                category = "SECURITY",
                entityType = "SESSION",
                entityId = session.id.toString()
            )
        }
    }

    @Transactional(readOnly = true)
    fun me(principal: UserPrincipal): UserDto =
        users.findById(principal.id).map { it.toDto() }
            .orElseThrow { ApiException.notFound("USER_NOT_FOUND", "User no longer exists") }

    private fun issueTokens(user: User, deviceLabel: String?): AuthResponse {
        val rawRefresh = ByteArray(32).also { random.nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val now = Instant.now()
        sessions.save(
            UserSession(
                userId = requireNotNull(user.id),
                refreshTokenHash = hashToken(rawRefresh),
                deviceLabel = deviceLabel?.trim()?.take(120),
                createdAt = now,
                expiresAt = now.plus(props.jwt.refreshTokenTtlDays, ChronoUnit.DAYS)
            )
        )
        return AuthResponse(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = rawRefresh,
            tokenType = "Bearer",
            expiresInSeconds = jwtService.accessTokenTtlSeconds(),
            user = user.toDto()
        )
    }

    private fun validatePassword(password: String) {
        if (password.length < 10) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must be at least 10 characters")
        }
        if (password.none { it.isLetter() } || password.none { it.isDigit() }) {
            throw ApiException.badRequest("WEAK_PASSWORD", "Password must contain at least one letter and one digit")
        }
    }

    private fun hashToken(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
