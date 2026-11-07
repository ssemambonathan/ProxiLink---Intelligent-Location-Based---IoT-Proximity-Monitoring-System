package com.proxilink.backend.security

import com.proxilink.backend.auth.User
import com.proxilink.backend.config.AppProperties
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(private val props: AppProperties) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val key: SecretKey = run {
        val configured = props.jwt.secret.trim()
        if (configured.isEmpty()) {
            log.warn(
                "JWT_SECRET is not configured. Generating an ephemeral development key. " +
                    "All tokens become invalid on restart; set JWT_SECRET (>= 32 bytes) in production."
            )
            val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
            Keys.hmacShaKeyFor(bytes)
        } else {
            val bytes = configured.toByteArray(StandardCharsets.UTF_8)
            require(bytes.size >= 32) { "JWT_SECRET must contain at least 32 bytes" }
            Keys.hmacShaKeyFor(bytes)
        }
    }

    fun generateAccessToken(user: User): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("email", user.email)
            .claim("org", user.organization)
            .claim("roles", user.roles.map { it.name })
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(props.jwt.accessTokenTtlSeconds)))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): Claims =
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload

    fun accessTokenTtlSeconds(): Long = props.jwt.accessTokenTtlSeconds
}
