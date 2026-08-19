package com.nimba.shared

import com.nimba.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The API is reached through the `web` service, which proxies every `/api` path to
 * this service (web/proxy.ts). That hop replaces the public host with an internal one,
 * so without `server.forward-headers-strategy` the CORS filter compares the
 * browser's `Origin` against the *internal* host, never matches, and rejects every
 * write with 403 "Invalid CORS request" — while reads sail through, because
 * browsers only attach `Origin` to writes. That asymmetry is what made the
 * bootstrap page able to report "no admin yet" but unable to create one, and made
 * the same call succeed from Swagger UI (same-origin with this service).
 *
 * These tests pin the contract from the outside, over real HTTP: the login
 * endpoint is used only as a convenient public POST — reaching it at all (401 for
 * unknown credentials) is the assertion, not the authentication outcome.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProxiedOriginTest(
    @Value("\${local.server.port}") private val port: Int,
) {
    private companion object {
        /** A public origin deliberately absent from `nimba.cors.allowed-origins`. */
        const val PUBLIC_ORIGIN = "http://nimba.banque.test:3000"
        const val PUBLIC_HOST = "nimba.banque.test:3000"
    }

    private fun login(headers: Map<String, String>): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"nobody@banque.test","password":"whatever"}"""))
                .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    @Test
    fun `a write forwarded by the proxy is treated as same-origin and reaches the endpoint`() {
        val response =
            login(
                mapOf(
                    "Origin" to PUBLIC_ORIGIN,
                    "X-Forwarded-Host" to PUBLIC_HOST,
                    "X-Forwarded-Proto" to "http",
                    "X-Forwarded-Port" to "3000",
                ),
            )

        assertEquals(401, response.statusCode(), "the request must reach the login endpoint, not be cut off by CORS")
        assertTrue(
            "Invalid CORS request" !in response.body(),
            "a same-origin write behind the proxy must never be rejected as cross-origin",
        )
    }

    @Test
    fun `a write from an unlisted origin with no forwarded headers is still rejected`() {
        val response = login(mapOf("Origin" to PUBLIC_ORIGIN))

        assertEquals(403, response.statusCode())
        assertTrue("Invalid CORS request" in response.body())
    }

    @Test
    fun `a read carries no origin and is unaffected`() {
        val response =
            HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/auth/bootstrap")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(200, response.statusCode())
    }
}
