package com.proxilink.backend.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.proxilink.backend.auth.UserRepository
import com.proxilink.backend.common.ErrorResponse
import com.proxilink.backend.common.RateLimitFilter
import com.proxilink.backend.config.AppProperties
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    private val jwtService: JwtService,
    private val userRepository: UserRepository,
    private val props: AppProperties,
    private val objectMapper: ObjectMapper
) {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(12)

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = props.cors.allowedOrigins
        config.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.exposedHeaders = listOf("Location")
        config.allowCredentials = false
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        val jwtFilter = JwtAuthenticationFilter(jwtService, userRepository)
        val rateLimitFilter = RateLimitFilter(props.rateLimit, objectMapper)

        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { it.frameOptions { frame -> frame.sameOrigin() } }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                auth.requestMatchers(HttpMethod.POST,
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh"
                ).permitAll()
                auth.requestMatchers(
                    "/api/v1/config/public",
                    "/ws/**",
                    "/actuator/health/**",
                    "/actuator/info",
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/h2-console/**"
                ).permitAll()
                auth.requestMatchers(
                    "/api/v1/users/**",
                    "/api/v1/audit-logs/**",
                    "/api/v1/settings/**",
                    "/api/v1/admin/**"
                ).hasRole("ADMIN")
                auth.anyRequest().authenticated()
            }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint { request, response, _ ->
                    writeError(response, 401, "UNAUTHORIZED", "Authentication required", request.requestURI)
                }
                handling.accessDeniedHandler { request, response, _ ->
                    writeError(response, 403, "FORBIDDEN", "Access denied", request.requestURI)
                }
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter::class.java)

        return http.build()
    }

    private fun writeError(
        response: HttpServletResponse,
        status: Int,
        code: String,
        message: String,
        path: String
    ) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        response.writer.write(
            objectMapper.writeValueAsString(
                ErrorResponse(
                    status = status,
                    error = if (status == 401) "Unauthorized" else "Forbidden",
                    code = code,
                    message = message,
                    path = path
                )
            )
        )
    }
}
