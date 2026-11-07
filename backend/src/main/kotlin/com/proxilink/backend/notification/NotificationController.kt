package com.proxilink.backend.notification

import com.proxilink.backend.security.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(private val notificationService: NotificationService) {

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ) = notificationService.list(principal.id, unreadOnly, page, size)

    @PostMapping("/{id}/read")
    fun markRead(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: UUID
    ): ResponseEntity<Void> {
        notificationService.markRead(principal.id, id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/read-all")
    fun markAllRead(@AuthenticationPrincipal principal: UserPrincipal): ResponseEntity<Void> {
        notificationService.markAllRead(principal.id)
        return ResponseEntity.noContent().build()
    }
}
