package com.proxilink.backend.bootstrap

import com.proxilink.backend.admin.AuditService
import com.proxilink.backend.auth.RoleRepository
import com.proxilink.backend.auth.User
import com.proxilink.backend.auth.UserRepository
import com.proxilink.backend.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Creates the first administrator for a production deployment.
 *
 * Runs only in the prod profile, only when explicitly enabled, and only when the
 * users table is empty. The demo seeder is intentionally unavailable in prod, so
 * this is the supported way to obtain an initial admin account without SQL.
 */
@Component
@Profile("prod")
class BootstrapAdmin(
    private val props: AppProperties,
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val audit: AuditService
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val config = props.bootstrap
        if (!config.enabled) return

        val email = config.adminEmail.trim().lowercase()
        val password = config.adminPassword
        require(email.contains("@")) { "BOOTSTRAP_ADMIN_EMAIL must be a valid email" }
        require(password.length >= 12) { "BOOTSTRAP_ADMIN_PASSWORD must be at least 12 characters" }
        require(password.any { it.isLetter() } && password.any { it.isDigit() }) {
            "BOOTSTRAP_ADMIN_PASSWORD must contain a letter and a digit"
        }
        require(!password.startsWith("CHANGE_ME", ignoreCase = true)) {
            "BOOTSTRAP_ADMIN_PASSWORD still contains the example placeholder"
        }

        if (users.count() > 0) {
            log.info("Bootstrap admin skipped: {} user account(s) already exist", users.count())
            return
        }

        val adminRole = roles.findByName("ADMIN") ?: error("ADMIN role missing; check Flyway seed data")
        val userRole = roles.findByName("USER") ?: error("USER role missing; check Flyway seed data")
        val now = Instant.now()
        val user = users.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(password),
                displayName = config.displayName.trim().ifEmpty { "ProxiLink Administrator" },
                organization = config.organization.trim().ifEmpty { "ProxiLink" },
                roles = mutableSetOf(adminRole, userRole),
                createdAt = now,
                updatedAt = now
            )
        )
        audit.record(
            actorId = user.id,
            action = "BOOTSTRAP_ADMIN",
            category = "SECURITY",
            entityType = "USER",
            entityId = user.id.toString(),
            details = "organization=${user.organization}"
        )
        log.warn(
            "Created bootstrap administrator {}. Sign in and change the password; then disable BOOTSTRAP_ADMIN_ENABLED.",
            email
        )
    }
}
