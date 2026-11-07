package com.proxilink.backend.common

import org.springframework.http.HttpStatus
import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String,
    val violations: Map<String, String>? = null
)

class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String
) : RuntimeException(message) {
    companion object {
        fun badRequest(code: String, message: String) = ApiException(HttpStatus.BAD_REQUEST, code, message)
        fun unauthorized(code: String, message: String) = ApiException(HttpStatus.UNAUTHORIZED, code, message)
        fun forbidden(code: String, message: String) = ApiException(HttpStatus.FORBIDDEN, code, message)
        fun notFound(code: String, message: String) = ApiException(HttpStatus.NOT_FOUND, code, message)
        fun conflict(code: String, message: String) = ApiException(HttpStatus.CONFLICT, code, message)
        fun unprocessable(code: String, message: String) = ApiException(HttpStatus.UNPROCESSABLE_ENTITY, code, message)
    }
}
