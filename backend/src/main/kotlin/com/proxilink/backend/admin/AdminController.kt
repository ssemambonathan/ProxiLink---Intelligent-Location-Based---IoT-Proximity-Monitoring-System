package com.proxilink.backend.admin

import com.proxilink.backend.common.PageResponse
import com.proxilink.backend.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class AdminController(
    private val adminUserService: AdminUserService,
    private val auditLogRepository: AuditLogRepository
) {

    @GetMapping("/users")
    fun listUsers(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): PageResponse<com.proxilink.backend.auth.UserDto> = adminUserService.list(q, page, size)

    @PatchMapping("/users/{id}")
    fun updateUser(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID,
        @RequestBody @Valid request: UserUpdateRequest
    ) = adminUserService.update(principal, id, request)

    @GetMapping("/audit-logs")
    fun auditLogs(
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) actorId: UUID?,
        @RequestParam(required = false) from: Instant?,
        @RequestParam(required = false) to: Instant?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int
    ): PageResponse<AuditLogDto> {
        val result = auditLogRepository.search(
            action?.trim()?.takeIf { it.isNotEmpty() },
            category?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            actorId,
            from,
            to,
            PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        )
        return PageResponse(
            items = result.content.map { it.toDto() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages
        )
    }
}
