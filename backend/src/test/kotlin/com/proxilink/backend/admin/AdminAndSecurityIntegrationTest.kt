package com.proxilink.backend.admin

import com.proxilink.backend.IntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdminAndSecurityIntegrationTest : IntegrationTestSupport() {

    @Test
    fun `administrative endpoints are forbidden to regular users`() {
        val user = registerUser()
        assertError(get("/api/v1/users", user.accessToken), 403, "FORBIDDEN")
        assertError(get("/api/v1/audit-logs", user.accessToken), 403, "FORBIDDEN")
        assertError(get("/api/v1/settings", user.accessToken), 403, "FORBIDDEN")
        assertError(patch("/api/v1/settings", mapOf("scanIntervalSeconds" to 20), user.accessToken), 403, "FORBIDDEN")
    }

    @Test
    fun `administrators can list users and inspect audit logs`() {
        val admin = registerUser(admin = true)
        val user = registerUser()
        createDevice(user.accessToken, name = "Audited Device")

        val users = json(get("/api/v1/users?size=100", admin.accessToken))
        assertTrue(users["totalElements"].asInt() >= 2)
        assertTrue(users["items"].any { it["email"].asText() == user.email })

        val audit = json(get("/api/v1/audit-logs?action=DEVICE_REGISTERED&size=50", admin.accessToken))
        assertTrue(audit["totalElements"].asInt() >= 1)
        assertEquals("DEVICE", audit["items"][0]["category"].asText())
        assertTrue(audit["items"].any { it["details"].asText().contains("Audited Device") })
    }

    @Test
    fun `settings update is validated and exposed to clients`() {
        val admin = registerUser(admin = true)
        val original = json(get("/api/v1/settings", admin.accessToken))
        try {
            val updated = patch(
                "/api/v1/settings",
                mapOf(
                    "proximityThresholds" to mapOf(
                        "immediate" to -45,
                        "veryClose" to -60,
                        "nearby" to -75,
                        "far" to -90
                    ),
                    "locationIntervalSeconds" to 45
                ),
                admin.accessToken
            )
            assertEquals(200, updated.statusCode.value(), updated.body)
            assertEquals(-45, json(updated)["proximityThresholds"]["immediate"].asInt())
            assertEquals(45, json(updated)["locationIntervalSeconds"].asInt())

            val publicConfig = get("/api/v1/config/public")
            assertEquals(200, publicConfig.statusCode.value(), publicConfig.body)
            assertEquals(-45, json(publicConfig)["proximityThresholds"]["immediate"].asInt())
            assertEquals(45, json(publicConfig)["locationIntervalSeconds"].asInt())
            assertTrue(!publicConfig.body!!.contains("dataRetentionDays"), "retention policy must not be public")

            val device = createDevice(admin.accessToken)
            ingestProximity(admin.accessToken, listOf(proximityEventPayload(device["id"].asText(), rssi = -55)))
            val stored = json(get("/api/v1/events/proximity?deviceId=${device["id"].asText()}", admin.accessToken))["items"][0]
            assertEquals("VERY_CLOSE", stored["proximityLevel"].asText())

            val invalid = patch(
                "/api/v1/settings",
                mapOf("proximityThresholds" to mapOf("immediate" to -80, "veryClose" to -60, "nearby" to -70, "far" to -90)),
                admin.accessToken
            )
            assertError(invalid, 422, "INVALID_THRESHOLDS")
        } finally {
            val restore = patch(
                "/api/v1/settings",
                mapOf(
                    "proximityThresholds" to original["proximityThresholds"],
                    "locationIntervalSeconds" to original["locationIntervalSeconds"].asInt(),
                    "scanIntervalSeconds" to original["scanIntervalSeconds"].asInt(),
                    "offlineTimeoutSeconds" to original["offlineTimeoutSeconds"].asInt(),
                    "dataRetentionDays" to original["dataRetentionDays"].asInt()
                ),
                admin.accessToken
            )
            assertEquals(200, restore.statusCode.value(), restore.body)
        }
    }

    @Test
    fun `administrators cannot demote or disable themselves`() {
        val admin = registerUser(admin = true)
        assertError(
            patch("/api/v1/users/${admin.id}", mapOf("roles" to listOf("USER")), admin.accessToken),
            422,
            "SELF_DEMOTE"
        )
        assertError(
            patch("/api/v1/users/${admin.id}", mapOf("enabled" to false), admin.accessToken),
            422,
            "SELF_DISABLE"
        )
    }

    @Test
    fun `administrators can disable another account immediately`() {
        val admin = registerUser(admin = true)
        val victim = registerUser()

        val disabled = patch("/api/v1/users/${victim.id}", mapOf("enabled" to false), admin.accessToken)
        assertEquals(200, disabled.statusCode.value(), disabled.body)
        assertTrue(!json(disabled)["enabled"].asBoolean())

        assertError(login(victim.email, victim.password), 403, "ACCOUNT_DISABLED")
        assertError(get("/api/v1/auth/me", victim.accessToken), 401, "UNAUTHORIZED")

        assertError(
            patch("/api/v1/users/${victim.id}", mapOf("roles" to listOf("SUPERUSER")), admin.accessToken),
            400,
            "UNKNOWN_ROLE"
        )
        assertError(
            patch("/api/v1/users/${victim.id}", mapOf("roles" to emptyList<String>()), admin.accessToken),
            400,
            "ROLES_REQUIRED"
        )
    }

    @Test
    fun `system status reports a healthy database and disabled redis`() {
        val admin = registerUser(admin = true)
        val status = get("/api/v1/system/status", admin.accessToken)
        assertEquals(200, status.statusCode.value(), status.body)
        assertEquals("UP", json(status)["status"].asText())
        assertEquals("UP", json(status)["database"].asText())
        assertEquals("DISABLED", json(status)["redis"].asText())
        assertEquals("1.0.0", json(status)["version"].asText())
    }
}
