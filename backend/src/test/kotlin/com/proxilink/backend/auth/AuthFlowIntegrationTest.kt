package com.proxilink.backend.auth

import com.proxilink.backend.IntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AuthFlowIntegrationTest : IntegrationTestSupport() {

    @Test
    fun `registration returns tokens and profile without leaking password hash`() {
        val user = registerUser()
        val profile = get("/api/v1/auth/me", user.accessToken)
        assertEquals(200, profile.statusCode.value(), profile.body)
        val payload = json(profile)
        assertEquals(user.email, payload["email"].asText())
        assertEquals(user.organization, payload["organization"].asText())
        assertEquals(listOf("USER"), payload["roles"].map { it.asText() })
        assertTrue(payload["enabled"].asBoolean())
        assertFalse(profile.body!!.contains("passwordHash", ignoreCase = true))
    }

    @Test
    fun `duplicate email registration is rejected`() {
        val user = registerUser()
        val response = post(
            "/api/v1/auth/register",
            mapOf(
                "email" to user.email,
                "password" to "ProxiLink!Other2026",
                "displayName" to "Second Account",
                "organization" to user.organization
            )
        )
        assertError(response, 409, "EMAIL_TAKEN")
    }

    @Test
    fun `weak passwords are rejected`() {
        val short = post(
            "/api/v1/auth/register",
            mapOf("email" to uniqueEmail(), "password" to "short1", "displayName" to "Short")
        )
        assertError(short, 400, "VALIDATION_FAILED")

        val noDigit = post(
            "/api/v1/auth/register",
            mapOf("email" to uniqueEmail(), "password" to "onlylettershere", "displayName" to "Letters")
        )
        assertError(noDigit, 400, "WEAK_PASSWORD")
    }

    @Test
    fun `login rejects wrong password and unknown account`() {
        val user = registerUser()
        assertError(login(user.email, "Wrong!Password2026"), 401, "INVALID_CREDENTIALS")
        assertError(login("missing-${UUID.randomUUID()}@proxilink.test", "ProxiLink!Test2026"), 401, "INVALID_CREDENTIALS")
    }

    @Test
    fun `login accepts a valid account`() {
        val user = registerUser()
        val response = login(user.email, user.password)
        assertEquals(200, response.statusCode.value(), response.body)
        val payload = json(response)
        assertEquals("Bearer", payload["tokenType"].asText())
        assertEquals(1800, payload["expiresInSeconds"].asLong())
        assertTrue(payload["accessToken"].asText().isNotBlank())
        assertTrue(payload["refreshToken"].asText().isNotBlank())
    }

    @Test
    fun `refresh rotates the token and the old refresh token cannot be reused`() {
        val user = registerUser()
        val first = post("/api/v1/auth/refresh", mapOf("refreshToken" to user.refreshToken))
        assertEquals(200, first.statusCode.value(), first.body)
        val rotated = json(first)
        assertTrue(rotated["refreshToken"].asText() != user.refreshToken)

        val replay = post("/api/v1/auth/refresh", mapOf("refreshToken" to user.refreshToken))
        assertError(replay, 401, "INVALID_REFRESH_TOKEN")

        val second = post("/api/v1/auth/refresh", mapOf("refreshToken" to rotated["refreshToken"].asText()))
        assertEquals(200, second.statusCode.value(), second.body)
    }

    @Test
    fun `logout revokes the refresh token`() {
        val user = registerUser()
        val logout = post("/api/v1/auth/logout", mapOf("refreshToken" to user.refreshToken), user.accessToken)
        assertEquals(204, logout.statusCode.value(), logout.body)

        val refresh = post("/api/v1/auth/refresh", mapOf("refreshToken" to user.refreshToken))
        assertError(refresh, 401, "INVALID_REFRESH_TOKEN")
    }

    @Test
    fun `disabled account cannot log in and its access token stops working`() {
        val user = registerUser()
        val entity = userRepository.findById(user.id).orElseThrow()
        entity.enabled = false
        userRepository.save(entity)

        assertError(login(user.email, user.password), 403, "ACCOUNT_DISABLED")
        assertError(get("/api/v1/auth/me", user.accessToken), 401, "UNAUTHORIZED")
    }

    @Test
    fun `protected endpoints reject missing or invalid tokens`() {
        assertError(get("/api/v1/devices"), 401, "UNAUTHORIZED")
        assertError(get("/api/v1/devices", "not-a-jwt"), 401, "UNAUTHORIZED")
    }

    @Test
    fun `registration validates email format`() {
        val response = post(
            "/api/v1/auth/register",
            mapOf("email" to "not-an-email", "password" to "ProxiLink!Test2026", "displayName" to "Bad Email")
        )
        assertError(response, 400, "VALIDATION_FAILED")
        assertTrue(response.body!!.contains("email"))
    }
}
