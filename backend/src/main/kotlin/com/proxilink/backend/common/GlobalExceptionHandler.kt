package com.proxilink.backend.common

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(ex: ApiException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        build(ex.status, ex.code, ex.message, request.requestURI)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        val violations = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                status = 400,
                error = "Bad Request",
                code = "VALIDATION_FAILED",
                message = "Request validation failed",
                path = request.requestURI,
                violations = violations
            )
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException, request: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Malformed or missing JSON body", request.requestURI)

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException, request: HttpServletRequest) =
        build(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Invalid value for '${ex.name}'", request.requestURI)

    @ExceptionHandler(AccessDeniedException::class)
    fun handleDenied(ex: AccessDeniedException, request: HttpServletRequest) =
        build(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied", request.requestURI)

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(ex: DataIntegrityViolationException, request: HttpServletRequest) =
        build(HttpStatus.CONFLICT, "CONFLICT", "The request conflicts with existing data", request.requestURI)

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception on {} {}", request.method, request.requestURI, ex)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", request.requestURI)
    }

    private fun build(status: HttpStatus, code: String, message: String, path: String) =
        ResponseEntity.status(status).body(
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                code = code,
                message = message,
                path = path
            )
        )
}
