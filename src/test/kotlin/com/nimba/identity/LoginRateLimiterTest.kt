package com.nimba.identity

import com.nimba.identity.internal.LoginRateLimitProperties
import com.nimba.identity.internal.LoginRateLimiter
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit tests for the in-memory login throttle, exercising the per-email and
 * per-IP windows, success reset, and window expiry against a controllable clock —
 * no Spring context or database needed.
 */
class LoginRateLimiterTest {
    private class MutableClock(
        var current: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId?): Clock = this

        override fun instant(): Instant = current

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private val clock = MutableClock(Instant.parse("2026-01-01T08:00:00Z"))
    private val limiter = LoginRateLimiter(LoginRateLimitProperties(maxAttempts = 3, window = Duration.ofMinutes(15)), clock)

    @Test
    fun `locks a targeted email after the maximum failures, regardless of source IP`() {
        limiter.recordFailure("victim@banque.test", "10.0.0.1")
        limiter.recordFailure("victim@banque.test", "10.0.0.2")
        limiter.recordFailure("victim@banque.test", "10.0.0.3")

        // The email is now locked even from a brand-new IP.
        assertFailsWith<ResponseStatusException> {
            limiter.assertNotLocked("victim@banque.test", "10.0.0.9")
        }
    }

    @Test
    fun `locks a source IP after the maximum failures, regardless of targeted email`() {
        limiter.recordFailure("a@banque.test", "10.0.0.7")
        limiter.recordFailure("b@banque.test", "10.0.0.7")
        limiter.recordFailure("c@banque.test", "10.0.0.7")

        assertFailsWith<ResponseStatusException> {
            limiter.assertNotLocked("d@banque.test", "10.0.0.7")
        }
    }

    @Test
    fun `a successful login clears the email failure count`() {
        limiter.recordFailure("user@banque.test", "10.0.0.1")
        limiter.recordFailure("user@banque.test", "10.0.0.2")

        limiter.recordSuccess("user@banque.test")

        // After reset, a further failure leaves the email well under the limit, so
        // a check from an unused IP passes (without reset it would be the 3rd, locking).
        limiter.recordFailure("user@banque.test", "10.0.0.3")
        limiter.assertNotLocked("user@banque.test", "10.0.0.9")
    }

    @Test
    fun `the lock expires once the window elapses`() {
        limiter.recordFailure("expire@banque.test", "10.0.0.1")
        limiter.recordFailure("expire@banque.test", "10.0.0.2")
        limiter.recordFailure("expire@banque.test", "10.0.0.3")
        assertFailsWith<ResponseStatusException> { limiter.assertNotLocked("expire@banque.test", "10.0.0.9") }

        clock.advance(Duration.ofMinutes(16))

        // No exception: the window has passed and the counter is considered reset.
        limiter.assertNotLocked("expire@banque.test", "10.0.0.9")
    }

    @Test
    fun `the email match is case-insensitive and trims surrounding whitespace`() {
        limiter.recordFailure("Mixed@Banque.Test", "10.0.0.1")
        limiter.recordFailure("  mixed@banque.test  ", "10.0.0.2")
        limiter.recordFailure("MIXED@BANQUE.TEST", "10.0.0.3")

        assertFailsWith<ResponseStatusException> {
            limiter.assertNotLocked("mixed@banque.test", "10.0.0.9")
        }
    }

    @Test
    fun `does not lock below the configured maximum`() {
        limiter.recordFailure("calm@banque.test", "10.0.0.1")
        limiter.recordFailure("calm@banque.test", "10.0.0.1")

        // Two failures with maxAttempts=3: still allowed. Should not throw.
        limiter.assertNotLocked("calm@banque.test", "10.0.0.1")
        assertEquals(Unit, Unit)
    }
}
