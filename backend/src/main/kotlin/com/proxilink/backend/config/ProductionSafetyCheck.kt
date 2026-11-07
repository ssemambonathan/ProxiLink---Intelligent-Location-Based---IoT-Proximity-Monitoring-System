package com.proxilink.backend.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * Fails a production start with an actionable message instead of silently running
 * with an ephemeral signing key or a wildcard WebSocket origin.
 */
@Component
@Profile("prod")
class ProductionSafetyCheck(private val props: AppProperties) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        check(props.jwt.secret.toByteArray().size >= 32) {
            "JWT_SECRET must be set to at least 32 bytes in production"
        }
        check(props.websocket.allowedOrigins.none { it.trim() == "*" }) {
            "WS_ALLOWED_ORIGINS must list explicit origins in production (wildcard is not allowed)"
        }
        check(props.cors.allowedOrigins.isNotEmpty() && props.cors.allowedOrigins.none { it.trim() == "*" }) {
            "CORS_ALLOWED_ORIGINS must list explicit origins in production"
        }
    }
}
