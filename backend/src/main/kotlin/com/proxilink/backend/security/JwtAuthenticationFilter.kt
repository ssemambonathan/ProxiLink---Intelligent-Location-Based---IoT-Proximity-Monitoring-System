package com.proxilink.backend.security

import com.proxilink.backend.auth.UserRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val userRepository: UserRepository
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().authentication == null) {
            val token = header.substringAfter("Bearer ").trim()
            try {
                val claims = jwtService.parse(token)
                val userId = UUID.fromString(claims.subject)
                val user = userRepository.findById(userId).orElse(null)
                if (user != null && user.enabled) {
                    val principal = UserPrincipal.from(user)
                    val authentication = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.authorities
                    )
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authentication
                }
            } catch (ex: Exception) {
                log.debug("Rejected bearer token: {}", ex.message)
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }
}
