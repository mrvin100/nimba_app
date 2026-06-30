package com.nimba.identity.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Development/staging seed configuration (NIMBA-9C). Disabled by default, so it
 * never runs in production unless explicitly enabled. The password is supplied via
 * an environment variable and is never committed; if it is blank while seeding is
 * enabled, the seeder skips rather than creating an account with a known password.
 */
@ConfigurationProperties("nimba.seed")
data class SeedProperties(
    val enabled: Boolean = false,
    val driName: String = "Analyste DRI",
    val driEmail: String = "analyste@nimba.local",
    val driPassword: String? = null,
)
