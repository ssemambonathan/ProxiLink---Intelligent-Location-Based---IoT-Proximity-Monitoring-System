package com.proxilink.backend.realtime

import com.proxilink.backend.auth.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@Component
class JwtHandshakeInterceptor(
    private val jwtService: com.proxilink.backend.security.JwtService,
    private val userRepository: UserRepository
) : HandshakeInterceptor {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val token = request.uri.query
            ?.split("&")
            ?.firstOrNull { it.startsWith("token=") }
            ?.substringAfter("token=")
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8) }
            ?: request.headers.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()

        if (token.isNullOrBlank()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            return false
        }
        return try {
            val claims = jwtService.parse(token)
            val user = userRepository.findById(UUID.fromString(claims.subject)).orElse(null)
            if (user == null || !user.enabled) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED)
                false
            } else {
                attributes["userId"] = requireNotNull(user.id)
                attributes["organization"] = user.organization
                attributes["admin"] = user.roles.any { it.name == "ADMIN" }
                true
            }
        } catch (ex: Exception) {
            log.debug("WebSocket handshake rejected: {}", ex.message)
            response.setStatusCode(HttpStatus.UNAUTHORIZED)
            false
        }
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) = Unit
}
