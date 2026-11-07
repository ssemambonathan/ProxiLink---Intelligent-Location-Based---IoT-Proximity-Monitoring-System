package com.proxilink.backend.admin

import com.proxilink.backend.auth.RoleRepository
import com.proxilink.backend.auth.UserDto
import com.proxilink.backend.auth.UserRepository
import com.proxilink.backend.auth.toDto
import com.proxilink.backend.common.ApiException
import com.proxilink.backend.common.PageResponse
import com.proxilink.backend.security.UserPrincipal
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AdminUserService(
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val audit: AuditService
) {

    @Transactional(readOnly = true)
    fun list(query: String?, page: Int, size: Int): PageResponse<UserDto> {
        val result = users.search(
            query?.trim()?.takeIf { it.isNotEmpty() },
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

    @Transactional
    fun update(actor: UserPrincipal, id: UUID, request: UserUpdateRequest): UserDto {
        val user = users.findById(id)
            .orElseThrow { ApiException.notFound("USER_NOT_FOUND", "User not found") }

        if (actor.id == id) {
            if (request.enabled == false) {
                throw ApiException.unprocessable("SELF_DISABLE", "You cannot disable your own account")
            }
            if (request.roles != null && "ADMIN" !in request.roles) {
                throw ApiException.unprocessable("SELF_DEMOTE", "You cannot remove your own administrator role")
            }
        }

        request.enabled?.let { user.enabled = it }
        request.roles?.let { requested ->
            if (requested.isEmpty()) {
                throw ApiException.badRequest("ROLES_REQUIRED", "At least one role is required")
            }
            val resolved = requested.map { name ->
                roles.findByName(name.trim().uppercase())
                    ?: throw ApiException.badRequest("UNKNOWN_ROLE", "Unknown role: $name")
            }.toMutableSet()
            user.roles = resolved
        }
        user.updatedAt = Instant.now()

        audit.record(
            actorId = actor.id,
            action = "USER_UPDATE",
            category = "ADMIN",
            entityType = "USER",
            entityId = id.toString(),
            details = "enabled=${user.enabled}, roles=${user.roles.map { it.name }}"
        )
        return user.toDto()
    }
}
