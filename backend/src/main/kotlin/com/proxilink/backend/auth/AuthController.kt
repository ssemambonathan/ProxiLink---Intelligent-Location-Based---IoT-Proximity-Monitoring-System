package com.proxilink.backend.auth

import com.proxilink.backend.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/register")
    fun register(@RequestBody @Valid request: RegisterRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request))

    @PostMapping("/login")
    fun login(@RequestBody @Valid request: LoginRequest): AuthResponse = authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@RequestBody @Valid request: RefreshRequest): AuthResponse =
        authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    fun logout(
        @RequestBody @Valid request: RefreshRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<Void> {
        authService.logout(request.refreshToken, principal)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: UserPrincipal): UserDto = authService.me(principal)
}
