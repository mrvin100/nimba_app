package com.nimba.identity.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Login throttling configuration (NIMBA-9B). After [maxAttempts] failed attempts
 * within [window] — counted independently per email and per source IP — further
 * attempts are rejected until the window expires. In-memory and single-instance
 * by design; no distributed store is needed for this internal deployment.
 */
@ConfigurationProperties(prefix = "auth.login-rate-limit")
data class LoginRateLimitProperties(
    val maxAttempts: Int = 5,
    val window: Duration = Duration.ofMinutes(15),
)
