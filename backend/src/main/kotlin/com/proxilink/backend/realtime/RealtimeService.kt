package com.proxilink.backend.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class RealtimeService(private val objectMapper: ObjectMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class ClientSession(
        val session: WebSocketSession,
        val userId: UUID,
        val organization: String,
        val admin: Boolean
    )

    private val sessions = ConcurrentHashMap<String, ClientSession>()

    fun register(session: WebSocketSession, userId: UUID, organization: String, admin: Boolean) {
        sessions[session.id] = ClientSession(session, userId, organization, admin)
        log.info("WebSocket connected: user={}, admin={}, active={}", userId, admin, sessions.size)
    }

    fun unregister(session: WebSocketSession) {
        sessions.remove(session.id)
    }

    fun sessionCount(): Int = sessions.size

    fun send(session: WebSocketSession, type: String, payload: Any?) {
        if (!session.isOpen) return
        val frame = objectMapper.writeValueAsString(mapOf("type" to type, "payload" to payload))
        try {
            synchronized(session) {
                session.sendMessage(TextMessage(frame))
            }
        } catch (ex: Exception) {
            log.debug("WebSocket send failed; closing session {}", session.id, ex)
            runCatching { session.close(CloseStatus.SERVER_ERROR) }
            sessions.remove(session.id)
        }
    }

    /**
     * Fan-out rule: administrators receive everything; everyone else receives events from
     * their organization or notifications addressed to them.
     */
    fun broadcast(type: String, payload: Any?, organization: String? = null, userId: UUID? = null) {
        val frame = objectMapper.writeValueAsString(mapOf("type" to type, "payload" to payload))
        sessions.values.forEach { client ->
            val matches = client.admin ||
                (organization != null && client.organization == organization) ||
                (userId != null && client.userId == userId)
            if (!matches || !client.session.isOpen) return@forEach
            try {
                synchronized(client.session) {
                    client.session.sendMessage(TextMessage(frame))
                }
            } catch (ex: Exception) {
                log.debug("Dropping WebSocket client {}", client.session.id, ex)
                runCatching { client.session.close(CloseStatus.SERVER_ERROR) }
                sessions.remove(client.session.id)
            }
        }
    }
}
