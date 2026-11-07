package com.proxilink.backend.bootstrap

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
 * Development-only accounts so a fresh clone is demonstrable without a manual bootstrap step.
 * Credentials are documented in README and are never active in the prod profile or tests.
 */
@Component
@Profile("dev")
class DemoDataSeeder(
    private val props: AppProperties,
    private val users: UserRepository,
    private val roles: RoleRepository,
    private val passwordEncoder: PasswordEncoder
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        if (!props.demo.seedEnabled) return
        if (users.count() > 0) return

        val adminRole = roles.findByName("ADMIN") ?: error("ADMIN role missing; Flyway seed failed")
        val userRole = roles.findByName("USER") ?: error("USER role missing; Flyway seed failed")
        val now = Instant.now()

        users.save(
            User(
                email = props.demo.adminEmail.lowercase(),
                passwordHash = passwordEncoder.encode(props.demo.adminPassword),
                displayName = "ProxiLink Admin",
                organization = "Acme Labs",
                roles = mutableSetOf(adminRole, userRole),
                createdAt = now,
                updatedAt = now
            )
        )
        users.save(
            User(
                email = props.demo.userEmail.lowercase(),
                passwordHash = passwordEncoder.encode(props.demo.userPassword),
                displayName = "Demo Operator",
                organization = "Acme Labs",
                roles = mutableSetOf(userRole),
                createdAt = now,
                updatedAt = now
            )
        )
        log.info(
            "Seeded development accounts: {} (ADMIN) and {} (USER). Override via DEMO_* environment variables.",
            props.demo.adminEmail,
            props.demo.userEmail
        )
    }
}
