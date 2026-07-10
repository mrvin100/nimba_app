package com.nimba.creditcase

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.UserRepository
import com.nimba.seedDriAnalyst
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

/**
 * The type picker's reference data (NIMBA-47): every valid dossier type the
 * create-flow can offer, straight from the CaseTypePolicy registry.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CaseTypeEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun authenticatedClient(): HttpClient {
        seedDriAnalyst(users, passwordEncoder, "types@banque.test")
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"types@banque.test","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    @Test
    fun `lists every declared dossier type`() {
        val client = authenticatedClient()

        val response =
            client.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/credit-cases/types")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "AVEC_CONTRAT")
        assertContains(response.body(), "SANS_CONTRAT")
        assertContains(response.body(), "MC2_MUFFA")
        assertContains(response.body(), "\"generatesTraites\":false")
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

        val response =
            anonymous.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/credit-cases/types")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(401, response.statusCode())
    }
}
