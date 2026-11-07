package com.proxilink.backend.event

import com.proxilink.backend.IntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EventIngestIntegrationTest : IntegrationTestSupport() {

    @Test
    fun `proximity event is classified server side and updates device state`() {
        val user = registerUser()
        val device = createDevice(user.accessToken, name = "Lobby Beacon")
        val deviceId = device["id"].asText()
        val eventId = UUID.randomUUID().toString()

        val response = ingestProximity(user.accessToken, listOf(proximityEventPayload(deviceId, eventId, rssi = -58)))
        assertEquals(200, response.statusCode.value(), response.body)
        val result = json(response)
        assertEquals(1, result["accepted"].asInt())
        assertEquals(0, result["duplicates"].asInt())
        assertEquals(0, result["rejected"].size())

        val updated = json(get("/api/v1/devices/$deviceId", user.accessToken))
        assertEquals("NEARBY", updated["status"].asText())
        assertEquals(-58, updated["lastRssi"].asInt())
        assertEquals("VERY_CLOSE", updated["lastProximity"].asText())
        assertEquals(52.2297, updated["lastLocation"]["latitude"].asDouble())
        assertEquals(6.5, updated["lastLocation"]["accuracyM"].asDouble())

        val history = json(get("/api/v1/events/proximity?deviceId=$deviceId", user.accessToken))
        assertEquals(1, history["totalElements"].asInt())
        val event = history["items"][0]
        assertEquals(eventId, event["eventId"].asText())
        assertEquals("VERY_CLOSE", event["proximityLevel"].asText())
        assertTrue(event["estimatedDistanceM"].asDouble() > 0.0)
        assertEquals("Lobby Beacon", event["deviceName"].asText())
        assertTrue(event["metadata"]["simulated"].asBoolean())
    }

    @Test
    fun `administrators can ingest events for devices in any organization`() {
        val owner = registerUser()
        val device = createDevice(owner.accessToken)
        val admin = registerUser(admin = true)

        val response = ingestProximity(
            admin.accessToken,
            listOf(proximityEventPayload(device["id"].asText(), rssi = -49))
        )
        assertEquals(200, response.statusCode.value(), response.body)
        assertEquals(1, json(response)["accepted"].asInt())
    }

    @Test
    fun `duplicate event ids are idempotent inside a batch and across requests`() {
        val user = registerUser()
        val device = createDevice(user.accessToken)
        val deviceId = device["id"].asText()
        val sharedId = UUID.randomUUID().toString()

        val first = ingestProximity(
            user.accessToken,
            listOf(
                proximityEventPayload(deviceId, sharedId, rssi = -60),
                proximityEventPayload(deviceId, sharedId, rssi = -60)
            )
        )
        assertEquals(1, json(first)["accepted"].asInt(), first.body)
        assertEquals(1, json(first)["duplicates"].asInt(), first.body)

        val replay = ingestProximity(user.accessToken, listOf(proximityEventPayload(deviceId, sharedId)))
        assertEquals(0, json(replay)["accepted"].asInt(), replay.body)
        assertEquals(1, json(replay)["duplicates"].asInt(), replay.body)

        val history = json(get("/api/v1/events/proximity?deviceId=$deviceId", user.accessToken))
        assertEquals(1, history["totalElements"].asInt())
    }

    @Test
    fun `events for devices outside the caller organization are rejected`() {
        val owner = registerUser()
        val outsider = registerUser()
        val device = createDevice(owner.accessToken)

        val response = ingestProximity(
            outsider.accessToken,
            listOf(proximityEventPayload(device["id"].asText()))
        )
        assertEquals(200, response.statusCode.value(), response.body)
        assertEquals(0, json(response)["accepted"].asInt())
        assertEquals("device_not_accessible", json(response)["rejected"][0]["reason"].asText())
    }

    @Test
    fun `events for deactivated devices are rejected`() {
        val user = registerUser()
        val device = createDevice(user.accessToken)
        val deviceId = device["id"].asText()
        post("/api/v1/devices/$deviceId/deactivate", null, user.accessToken)

        val response = ingestProximity(user.accessToken, listOf(proximityEventPayload(deviceId)))
        assertEquals(0, json(response)["accepted"].asInt(), response.body)
        assertEquals("device_inactive", json(response)["rejected"][0]["reason"].asText())
    }

    @Test
    fun `unknown device id is reported as inaccessible rather than crashing`() {
        val user = registerUser()
        val response = ingestProximity(
            user.accessToken,
            listOf(proximityEventPayload(UUID.randomUUID().toString()))
        )
        assertEquals(0, json(response)["accepted"].asInt(), response.body)
        assertEquals("device_not_accessible", json(response)["rejected"][0]["reason"].asText())
    }

    @Test
    fun `events without a registered device are accepted with the caller organization`() {
        val user = registerUser()
        val response = ingestProximity(user.accessToken, listOf(proximityEventPayload(null, rssi = -77)))
        assertEquals(1, json(response)["accepted"].asInt(), response.body)

        val history = json(get("/api/v1/events/proximity", user.accessToken))
        assertEquals(1, history["totalElements"].asInt())
        assertEquals("NEARBY", history["items"][0]["proximityLevel"].asText())
    }

    @Test
    fun `future timestamps are clamped to server time`() {
        val user = registerUser()
        val device = createDevice(user.accessToken)
        val future = Instant.now().plusSeconds(3600).toString()

        val response = ingestProximity(
            user.accessToken,
            listOf(proximityEventPayload(device["id"].asText(), recordedAt = future))
        )
        assertEquals(1, json(response)["accepted"].asInt(), response.body)

        val stored = json(get("/api/v1/events/proximity?deviceId=${device["id"].asText()}", user.accessToken))["items"][0]
        val recordedAt = Instant.parse(stored["recordedAt"].asText())
        val receivedAt = Instant.parse(stored["receivedAt"].asText())
        assertTrue(recordedAt <= receivedAt.plusSeconds(1), "future timestamp must be clamped, got $recordedAt vs $receivedAt")
    }

    @Test
    fun `signal history supports level filter and pagination`() {
        val user = registerUser()
        val device = createDevice(user.accessToken)
        val deviceId = device["id"].asText()
        ingestProximity(
            user.accessToken,
            listOf(
                proximityEventPayload(deviceId, rssi = -45),
                proximityEventPayload(deviceId, rssi = -70),
                proximityEventPayload(deviceId, rssi = -85)
            )
        )

        val immediate = json(get("/api/v1/events/proximity?deviceId=$deviceId&proximityLevel=IMMEDIATE", user.accessToken))
        assertEquals(1, immediate["totalElements"].asInt())
        assertEquals("IMMEDIATE", immediate["items"][0]["proximityLevel"].asText())

        val paged = json(get("/api/v1/events/proximity?deviceId=$deviceId&page=0&size=2", user.accessToken))
        assertEquals(3, paged["totalElements"].asInt())
        assertEquals(2, paged["items"].size())
        assertEquals(2, paged["totalPages"].asInt())
    }

    @Test
    fun `invalid payloads fail validation`() {
        val user = registerUser()
        val device = createDevice(user.accessToken)
        val deviceId = device["id"].asText()

        assertError(ingestProximity(user.accessToken, listOf(proximityEventPayload(deviceId, rssi = 12))), 400, "VALIDATION_FAILED")
        assertError(post("/api/v1/events/proximity", mapOf("events" to emptyList<Any>()), user.accessToken), 400, "VALIDATION_FAILED")

        val invalidLatitude = proximityEventPayload(deviceId).toMutableMap().apply { put("latitude", 91.0) }
        assertError(ingestProximity(user.accessToken, listOf(invalidLatitude)), 400, "VALIDATION_FAILED")

        val oversizedBatch = (1..501).map { proximityEventPayload(deviceId) }
        assertError(ingestProximity(user.accessToken, oversizedBatch), 400, "VALIDATION_FAILED")
    }

    @Test
    fun `location events are stored and searchable`() {
        val user = registerUser()
        val eventId = UUID.randomUUID().toString()
        val response = post(
            "/api/v1/events/location",
            mapOf(
                "events" to listOf(
                    mapOf(
                        "eventId" to eventId,
                        "latitude" to 48.8566,
                        "longitude" to 2.3522,
                        "accuracyM" to 4.5,
                        "speedMps" to 1.2,
                        "altitudeM" to 35.0,
                        "recordedAt" to Instant.now().minusSeconds(3).toString()
                    )
                )
            ),
            user.accessToken
        )
        assertEquals(200, response.statusCode.value(), response.body)
        assertEquals(1, json(response)["accepted"].asInt())

        val history = json(get("/api/v1/events/location", user.accessToken))
        assertEquals(1, history["totalElements"].asInt())
        assertEquals(48.8566, history["items"][0]["latitude"].asDouble())
        assertEquals("GPS", history["items"][0]["source"].asText())
    }

    @Test
    fun `location payload outside valid coordinates is rejected`() {
        val user = registerUser()
        val response = post(
            "/api/v1/events/location",
            mapOf(
                "events" to listOf(
                    mapOf(
                        "eventId" to UUID.randomUUID().toString(),
                        "latitude" to 123.0,
                        "longitude" to 2.0,
                        "recordedAt" to Instant.now().toString()
                    )
                )
            ),
            user.accessToken
        )
        assertError(response, 400, "VALIDATION_FAILED")
    }

    @Test
    fun `event searches do not leak across organizations`() {
        val owner = registerUser()
        val outsider = registerUser()
        val device = createDevice(owner.accessToken)
        ingestProximity(owner.accessToken, listOf(proximityEventPayload(device["id"].asText())))

        val outsiderView = json(
            get("/api/v1/events/proximity?deviceId=${device["id"].asText()}", outsider.accessToken)
        )
        assertEquals(0, outsiderView["totalElements"].asInt())
    }
}
