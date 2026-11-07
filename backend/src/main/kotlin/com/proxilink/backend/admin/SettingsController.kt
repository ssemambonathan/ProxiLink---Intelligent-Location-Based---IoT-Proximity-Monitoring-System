package com.proxilink.backend.admin

import com.proxilink.backend.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class SettingsController(private val settingsService: SettingsService) {

    @GetMapping("/settings")
    fun get(): SettingsDto = settingsService.current()

    @PatchMapping("/settings")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody @Valid request: UpdateSettingsRequest
    ): SettingsDto = settingsService.update(request, principal.id)

    @GetMapping("/config/public")
    fun publicConfig(): PublicConfigDto = settingsService.publicConfig()
}
