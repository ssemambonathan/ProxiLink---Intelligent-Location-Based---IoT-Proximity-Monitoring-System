package com.proxilink.backend.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-IP token bucket. Kept in memory deliberately: a single-instance deployment protects
 * login/registration without an extra dependency. For multi-instance deployments the same
 * contract should be backed by Redis (see documentation/deployment.md).
 */
class RateLimitFilter(
    private val props: AppProperties.RateLimit,
    private val objectMapper: ObjectMapper
) : OncePerRequestFilter() {

    private data class Bucket(var tokens: Double, var lastRefillNanos: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/api/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authEndpoint = request.requestURI in setOf(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh"
        )
        val capacity = (if (authEndpoint) props.loginPerMinute else props.generalPerMinute).coerceAtLeast(1)
        val key = (if (authEndpoint) "auth:" else "api:") + clientIp(request)
        val now = System.nanoTime()
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity.toDouble(), now) }

        val allowed: Boolean
        synchronized(bucket) {
            val elapsedSeconds = (now - bucket.lastRefillNanos) / 1_000_000_000.0
            bucket.tokens = minOf(capacity.toDouble(), bucket.tokens + elapsedSeconds * capacity / 60.0)
            bucket.lastRefillNanos = now
            allowed = bucket.tokens >= 1.0
            if (allowed) bucket.tokens -= 1.0
        }

        if (!allowed) {
            response.status = 429
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write(
                objectMapper.writeValueAsString(
                    ErrorResponse(
                        status = 429,
                        error = "Too Many Requests",
                        code = "RATE_LIMITED",
                        message = "Rate limit exceeded. Retry later.",
                        path = request.requestURI
                    )
                )
            )
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr
}
