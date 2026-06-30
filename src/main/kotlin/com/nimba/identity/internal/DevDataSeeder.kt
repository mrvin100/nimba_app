package com.nimba.identity.internal

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Seeds a DRI analyst account on startup for development and staging (NIMBA-9C).
 * Runs only when `nimba.seed.enabled` is true and a password is provided, and is
 * idempotent (skips when the account already exists), so it is safe to leave on
 * across restarts. Production keeps seeding disabled.
 */
@Component
class DevDataSeeder(
    private val properties: SeedProperties,
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(DevDataSeeder::class.java)

    override fun run(args: ApplicationArguments) {
        if (!properties.enabled) return

        val password = properties.driPassword
        if (password.isNullOrBlank()) {
            log.warn("Seed is enabled but nimba.seed.dri-password is not set; skipping DRI analyst seed.")
            return
        }
        if (users.existsByEmail(properties.driEmail)) {
            return
        }

        users.save(
            User(
                fullName = properties.driName,
                email = properties.driEmail,
                passwordHash = requireNotNull(passwordEncoder.encode(password)),
            ),
        )
        log.info("Seeded DRI analyst account: {}", properties.driEmail)
    }
}
