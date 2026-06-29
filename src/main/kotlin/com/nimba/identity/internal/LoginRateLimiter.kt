package com.nimba.identity.internal

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fixed-window throttle for the login endpoint, counted independently
 * per targeted email and per source IP — covering both a focused attack on one
 * account and a spread of attempts from one source. Once the failure count in the
 * current window reaches the configured maximum, further attempts are rejected
 * with 429 (even with a correct password) until the window expires. A successful
 * login clears the email's failure count. The 429 message is generic and never
 * reveals whether the email exists.
 *
 * Single-instance only by design; the internal deployment runs one backend
 * instance, so a distributed store (Redis) is unnecessary at this stage.
 */
@Component
class LoginRateLimiter(
    private val properties: LoginRateLimitProperties,
    private val clock: Clock,
) {
    private class Attempts(
        val windowStart: Instant,
        var count: Int,
    )

    private val byEmail = ConcurrentHashMap<String, Attempts>()
    private val byIp = ConcurrentHashMap<String, Attempts>()

    /** Throws 429 if the email or the source IP is currently locked out. */
    fun assertNotLocked(
        email: String,
        clientIp: String,
    ) {
        if (isLocked(byEmail, normalize(email)) || isLocked(byIp, clientIp)) {
            throw ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Trop de tentatives de connexion ; veuillez réessayer plus tard",
            )
        }
    }

    fun recordFailure(
        email: String,
        clientIp: String,
    ) {
        register(byEmail, normalize(email))
        register(byIp, clientIp)
    }

    fun recordSuccess(email: String) {
        byEmail.remove(normalize(email))
    }

    private fun isLocked(
        counters: ConcurrentHashMap<String, Attempts>,
        key: String,
    ): Boolean {
        val attempts = counters[key] ?: return false
        if (windowExpired(attempts)) {
            counters.remove(key)
            return false
        }
        return attempts.count >= properties.maxAttempts
    }

    private fun register(
        counters: ConcurrentHashMap<String, Attempts>,
        key: String,
    ) {
        counters.compute(key) { _, existing ->
            if (existing == null || windowExpired(existing)) {
                Attempts(clock.instant(), 1)
            } else {
                existing.count++
                existing
            }
        }
    }

    private fun windowExpired(attempts: Attempts): Boolean = clock.instant().isAfter(attempts.windowStart.plus(properties.window))

    private fun normalize(email: String): String = email.trim().lowercase()
}
