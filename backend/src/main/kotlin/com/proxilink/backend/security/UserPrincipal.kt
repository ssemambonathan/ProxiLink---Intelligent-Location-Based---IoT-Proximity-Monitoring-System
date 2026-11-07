package com.proxilink.backend.security

import com.proxilink.backend.auth.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

class UserPrincipal(
    val id: UUID,
    val email: String,
    val displayName: String,
    val organization: String,
    val roles: Set<String>,
    private val passwordHash: String,
    private val enabledFlag: Boolean
) : UserDetails {

    val isAdmin: Boolean get() = roles.contains("ADMIN")

    override fun getAuthorities(): Collection<GrantedAuthority> =
        roles.map { SimpleGrantedAuthority("ROLE_$it") }

    override fun getPassword(): String = passwordHash
    override fun getUsername(): String = email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = enabledFlag

    companion object {
        fun from(user: User): UserPrincipal = UserPrincipal(
            id = requireNotNull(user.id) { "User id must be assigned" },
            email = user.email,
            displayName = user.displayName,
            organization = user.organization,
            roles = user.roles.map { it.name }.toSet(),
            passwordHash = user.passwordHash,
            enabledFlag = user.enabled
        )
    }
}
