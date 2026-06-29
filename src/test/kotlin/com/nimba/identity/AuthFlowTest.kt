package com.nimba.identity

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end session lifecycle over real HTTP, using the JDK HttpClient with a
 * cookie manager so the session cookie is carried exactly as a browser would —
 * proving login establishes a session, a protected route is reachable with it,
 * logout invalidates it, and the same cookie is refused afterwards (NIMBA-9 DoD).
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun seedUser(
        email: String,
        rawPassword: String,
    ) {
        users.saveAndFlush(
            User(
                fullName = "Analyste Test",
                email = email,
                passwordHash = requireNotNull(passwordEncoder.encode(rawPassword)),
            ),
        )
    }

    private fun client() = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

    private fun url(path: String) = URI("http://localhost:$port$path")

    private fun postJson(
        client: HttpClient,
        path: String,
        json: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(url(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(
        client: HttpClient,
        path: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(url(path)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `full session lifecycle - login, access protected route, logout, denied after`() {
        seedUser("flow@banque.test", "Sup3r-Secret")
        val client = client()

        val login = postJson(client, "/api/v1/auth/login", """{"email":"flow@banque.test","password":"Sup3r-Secret"}""")
        assertEquals(200, login.statusCode())
        assertContains(login.body(), "flow@banque.test")
        assertContains(login.body(), "DRI_ANALYST")
        assertTrue(
            login.headers().allValues("Set-Cookie").any { it.startsWith("NIMBASESSION=") },
            "login must establish a session cookie",
        )

        val me = get(client, "/api/v1/auth/me")
        assertEquals(200, me.statusCode())
        assertContains(me.body(), "flow@banque.test")

        val logout = postJson(client, "/api/v1/auth/logout", "")
        assertEquals(200, logout.statusCode())

        val meAfterLogout = get(client, "/api/v1/auth/me")
        assertEquals(401, meAfterLogout.statusCode())
    }

    @Test
    fun `invalid credentials are rejected with 401 and grant no authenticated session`() {
        seedUser("real@banque.test", "Correct-Horse")
        val client = client()

        val response = postJson(client, "/api/v1/auth/login", """{"email":"real@banque.test","password":"wrong-password"}""")
        assertEquals(401, response.statusCode())

        // Whatever cookie the failed request may carry, it must not be authenticated:
        // the protected route stays closed when reusing the same client.
        val me = get(client, "/api/v1/auth/me")
        assertEquals(401, me.statusCode())
    }

    @Test
    fun `protected route without a session is rejected with 401`() {
        val response = get(client(), "/api/v1/auth/me")
        assertEquals(401, response.statusCode())
    }
}
