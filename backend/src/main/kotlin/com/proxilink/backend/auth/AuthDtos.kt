package com.proxilink.backend.auth

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank @field:Email @field:Size(max = 255) val email: String,
    @field:NotBlank @field:Size(min = 10, max = 100) val password: String,
    @field:NotBlank @field:Size(max = 120) val displayName: String,
    @field:Size(max = 120) val organization: String? = null
)

data class LoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
    @field:Size(max = 120) val deviceLabel: String? = null
)

data class RefreshRequest(
    @field:NotBlank val refreshToken: String
)

data class UserDto(
    val id: UUID,
    val email: String,
    val displayName: String,
    val organization: String,
    val roles: List<String>,
    val enabled: Boolean,
    val createdAt: Instant,
    val lastLoginAt: Instant? = null
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresInSeconds: Long,
    val user: UserDto
)

fun User.toDto(): UserDto = UserDto(
    id = requireNotNull(id),
    email = email,
    displayName = displayName,
    organization = organization,
    roles = roles.map { it.name }.sorted(),
    enabled = enabled,
    createdAt = createdAt,
    lastLoginAt = lastLoginAt
)
