package com.proxilink.backend.realtime

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.IntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class RealtimeWebSocketIntegrationTest : IntegrationTestSupport() {

    @LocalServerPort
    private var port: Int = 0

    private val wsMapper = ObjectMapper()

    private class Collector(private val mapper: ObjectMapper) : TextWebSocketHandler() {
        val frames = LinkedBlockingQueue<JsonNode>()

        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            frames.add(mapper.readTree(message.payload))
        }
    }

    private fun connect(token: String): Pair<WebSocketSession, Collector> {
        val collector = Collector(wsMapper)
        val session = StandardWebSocketClient()
            .execute(collector, "ws://localhost:$port/ws?token=$token")
            .get(10, TimeUnit.SECONDS)
        return session to collector
    }

    private fun awaitFrame(collector: Collector, type: String, timeoutSeconds: Long = 10): JsonNode {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            val frame = collector.frames.poll(500, TimeUnit.MILLISECONDS) ?: continue
            if (frame["type"].asText() == type) return frame
        }
        throw AssertionError("Did not receive WebSocket frame of type $type")
    }

    @Test
    fun `authenticated client receives hello and live proximity events`() {
        val user = registerUser()
        val device = createDevice(user.accessToken, name = "Live Beacon")
        val deviceId = device["id"].asText()

        val (session, collector) = connect(user.accessToken)
        try {
            val hello = awaitFrame(collector, "hello")
            assertEquals(user.id.toString(), hello["payload"]["userId"].asText())

            val ingest = ingestProximity(user.accessToken, listOf(proximityEventPayload(deviceId, rssi = -55)))
            assertEquals(1, json(ingest)["accepted"].asInt(), ingest.body)

            val event = awaitFrame(collector, "proximity.event")
            assertEquals(deviceId, event["payload"]["deviceId"].asText())
            assertEquals("VERY_CLOSE", event["payload"]["proximityLevel"].asText())
            assertEquals(-55, event["payload"]["rssi"].asInt())
        } finally {
            session.close(CloseStatus.NORMAL)
        }
    }

    @Test
    fun `live events are not delivered to another organization`() {
        val owner = registerUser()
        val outsider = registerUser()
        val device = createDevice(owner.accessToken)

        val (session, collector) = connect(outsider.accessToken)
        try {
            awaitFrame(collector, "hello")
            ingestProximity(owner.accessToken, listOf(proximityEventPayload(device["id"].asText())))

            val leaked = collector.frames.poll(2, TimeUnit.SECONDS)
            assertNull(leaked, "unexpected cross-organization frame: ${leaked?.toString()}")
        } finally {
            session.close(CloseStatus.NORMAL)
        }
    }

    @Test
    fun `handshake with an invalid token is rejected`() {
        val failure = assertThrows(ExecutionException::class.java) {
            StandardWebSocketClient()
                .execute(Collector(wsMapper), "ws://localhost:$port/ws?token=invalid")
                .get(10, TimeUnit.SECONDS)
        }
        assertTrue(failure.cause != null || failure.message != null)
    }
}
