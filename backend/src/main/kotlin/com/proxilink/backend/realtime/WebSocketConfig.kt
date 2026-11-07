package com.proxilink.backend.realtime

import com.proxilink.backend.config.AppProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val handler: RealtimeWebSocketHandler,
    private val interceptor: JwtHandshakeInterceptor,
    private val props: AppProperties
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws")
            .addInterceptors(interceptor)
            .setAllowedOriginPatterns(*props.websocket.allowedOrigins.toTypedArray())
    }
}
