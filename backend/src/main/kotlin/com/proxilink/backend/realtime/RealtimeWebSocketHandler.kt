package com.proxilink.backend.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Instant
import java.util.UUID

@Component
class RealtimeWebSocketHandler(
    private val realtime: RealtimeService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val userId = session.attributes["userId"] as? UUID
        val organization = session.attributes["organization"] as? String
        if (userId == null || organization == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing authenticated attributes"))
            return
        }
        val admin = session.attributes["admin"] as? Boolean ?: false
        realtime.register(session, userId, organization, admin)
        realtime.send(
            session,
            "hello",
            mapOf(
                "sessionId" to session.id,
                "userId" to userId,
                "serverTime" to Instant.now()
            )
        )
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val type = runCatching {
            objectMapper.readTree(message.payload)["type"]?.asText()
        }.getOrNull()
        if (type == "ping") {
            realtime.send(session, "pong", emptyMap<String, Any?>())
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        realtime.unregister(session)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        realtime.unregister(session)
        runCatching { session.close(CloseStatus.SERVER_ERROR) }
    }
}
