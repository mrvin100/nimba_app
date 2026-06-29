package com.nimba.identity

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.LoginRateLimitProperties
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals

/**
 * Integration test for login throttling over real HTTP (NIMBA-9B DoD). The
 * dedicated `@TestPropertySource` gives this class its own application context, so
 * its throttle counters are isolated from other HTTP tests sharing the same source
 * IP (localhost). Each attempt uses a fresh client so only the email/IP throttle —
 * not any session state — affects the outcome.
 */
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["auth.login-rate-limit.max-attempts=5"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginRateLimitFlowTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val properties: LoginRateLimitProperties,
    @Value("\${local.server.port}") private val port: Int,
) {
    @Test
    fun `the sixth attempt is rejected with 429 even with the correct password`() {
        users.saveAndFlush(
            User(
                fullName = "Cible Throttle",
                email = "throttle@banque.test",
                passwordHash = requireNotNull(passwordEncoder.encode("Right-Password")),
            ),
        )

        val firstFive = (1..5).map { login("throttle@banque.test", "wrong-password").statusCode() }
        // Sixth attempt with the CORRECT password is still locked out.
        val sixth = login("throttle@banque.test", "Right-Password").statusCode()

        assertEquals(
            listOf(401, 401, 401, 401, 401, 429),
            firstFive + sixth,
            "config: maxAttempts=${properties.maxAttempts}, window=${properties.window}",
        )
    }

    private fun login(
        email: String,
        password: String,
    ): HttpResponse<String> =
        HttpClient
            .newBuilder()
            .cookieHandler(CookieManager())
            .build()
            .send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"$password"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
}
