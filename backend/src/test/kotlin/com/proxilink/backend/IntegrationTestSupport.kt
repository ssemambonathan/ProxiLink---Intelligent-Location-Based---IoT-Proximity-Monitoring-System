package com.proxilink.backend

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.auth.RoleRepository
import com.proxilink.backend.auth.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    @Autowired
    protected lateinit var rest: TestRestTemplate

    @Autowired
    protected lateinit var mapper: ObjectMapper

    @Autowired
    protected lateinit var userRepository: UserRepository

    @Autowired
    protected lateinit var roleRepository: RoleRepository

    protected data class TestUser(
        val id: UUID,
        val email: String,
        val password: String,
        val organization: String,
        val accessToken: String,
        val refreshToken: String
    )

    protected fun uniqueEmail(): String = "user-${UUID.randomUUID()}@proxilink.test"

    protected fun uniqueOrganization(): String = "Org ${UUID.randomUUID()}"

    protected fun uniqueHardwareId(): String = "HW-${UUID.randomUUID()}"

    protected fun uniqueMac(): String =
        UUID.randomUUID().toString().replace("-", "").substring(0, 12)
            .chunked(2).joinToString(":").uppercase()

    protected fun exchange(
        method: HttpMethod,
        path: String,
        requestBody: Any?,
        token: String?
    ): ResponseEntity<String> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)
        if (token != null) headers.setBearerAuth(token)
        return rest.exchange(path, method, HttpEntity(requestBody, headers), String::class.java)
    }

    protected fun post(path: String, requestBody: Any?, token: String? = null): ResponseEntity<String> =
        exchange(HttpMethod.POST, path, requestBody, token)

    protected fun get(path: String, token: String? = null): ResponseEntity<String> =
        exchange(HttpMethod.GET, path, null, token)

    protected fun patch(path: String, requestBody: Any?, token: String? = null): ResponseEntity<String> =
        exchange(HttpMethod.PATCH, path, requestBody, token)

    protected fun delete(path: String, token: String? = null): ResponseEntity<String> =
        exchange(HttpMethod.DELETE, path, null, token)

    protected fun json(response: ResponseEntity<String>): JsonNode =
        mapper.readTree(response.body ?: "null")

    protected fun assertError(response: ResponseEntity<String>, status: Int, code: String) {
        assertEquals(status, response.statusCode.value(), "unexpected status body=${response.body}")
        assertEquals(code, json(response)["code"].asText(), "unexpected error code body=${response.body}")
    }

    protected fun registerUser(
        organization: String = uniqueOrganization(),
        password: String = "ProxiLink!Test2026",
        admin: Boolean = false
    ): TestUser {
        val email = uniqueEmail()
        val response = post(
            "/api/v1/auth/register",
            mapOf(
                "email" to email,
                "password" to password,
                "displayName" to "Test Operator",
                "organization" to organization
            )
        )
        assertEquals(201, response.statusCode.value(), response.body)
        val payload = json(response)
        val user = TestUser(
            id = UUID.fromString(payload["user"]["id"].asText()),
            email = email,
            password = password,
            organization = organization,
            accessToken = payload["accessToken"].asText(),
            refreshToken = payload["refreshToken"].asText()
        )
        if (admin) promoteToAdmin(email)
        return user
    }

    protected fun promoteToAdmin(email: String) {
        val user = userRepository.findByEmailIgnoreCase(email).orElseThrow()
        val adminRole = roleRepository.findByName("ADMIN") ?: error("ADMIN role missing")
        val userRole = roleRepository.findByName("USER") ?: error("USER role missing")
        user.roles = mutableSetOf(adminRole, userRole)
        userRepository.save(user)
    }

    protected fun login(email: String, password: String): ResponseEntity<String> =
        post("/api/v1/auth/login", mapOf("email" to email, "password" to password))

    protected fun createDevice(
        token: String,
        name: String = "Test Beacon",
        hardwareId: String? = uniqueHardwareId(),
        bleMac: String? = uniqueMac(),
        category: String = "SENSOR",
        deviceType: String = "BLE Beacon",
        organization: String? = null
    ): JsonNode {
        val body = mutableMapOf<String, Any?>(
            "name" to name,
            "deviceType" to deviceType,
            "category" to category,
            "hardwareId" to hardwareId,
            "bleMac" to bleMac
        )
        if (organization != null) body["organization"] = organization
        val response = post("/api/v1/devices", body, token)
        assertEquals(201, response.statusCode.value(), response.body)
        return json(response)
    }

    protected fun proximityEventPayload(
        deviceId: String?,
        eventId: String = UUID.randomUUID().toString(),
        rssi: Int = -60,
        recordedAt: String = Instant.now().minusSeconds(2).toString(),
        metadata: Map<String, Any?>? = mapOf("simulated" to true)
    ): Map<String, Any?> = mapOf(
        "eventId" to eventId,
        "deviceId" to deviceId,
        "rssi" to rssi,
        "smoothedRssi" to rssi.toDouble(),
        "latitude" to 52.2297,
        "longitude" to 21.0122,
        "accuracyM" to 6.5,
        "recordedAt" to recordedAt,
        "metadata" to metadata
    )

    protected fun ingestProximity(
        token: String,
        events: List<Map<String, Any?>>
    ): ResponseEntity<String> = post("/api/v1/events/proximity", mapOf("events" to events), token)
}
