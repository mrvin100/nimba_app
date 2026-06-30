package com.nimba.identity.internal

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Seeds demo accounts on startup for development and staging (NIMBA-9C): a DRI
 * member, a DRI manager, and a platform admin, all sharing the configured
 * password. Runs only when `nimba.seed.enabled` is true and a password is provided,
 * and is idempotent (skips accounts that already exist), so it is safe across
 * restarts. Production keeps seeding disabled.
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
            log.warn("Seed is enabled but nimba.seed.dri-password is not set; skipping account seed.")
            return
        }
        val hash = requireNotNull(passwordEncoder.encode(password))

        seed(properties.driEmail, properties.driName, hash) { it.assign(Department.DRI, DepartmentRole.MEMBER) }
        seed(properties.managerEmail, "Manager DRI", hash) { it.assign(Department.DRI, DepartmentRole.MANAGER) }
        seed(properties.adminEmail, "Administrateur", hash) { it.platformAdmin = true }
    }

    private fun seed(
        email: String,
        fullName: String,
        passwordHash: String,
        configure: (User) -> Unit,
    ) {
        if (users.existsByEmail(email)) return
        val user = User(fullName = fullName, email = email, passwordHash = passwordHash)
        configure(user)
        users.save(user)
        log.info("Seeded account: {}", email)
    }
}
