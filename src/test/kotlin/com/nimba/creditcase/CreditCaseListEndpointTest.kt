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
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreditCaseListEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun authenticatedClient(): HttpClient {
        seedDriAnalyst(users, passwordEncoder, "lister@banque.test")
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val login =
            client.send(
                req("/api/v1/auth/login").POST(body("""{"email":"lister@banque.test","password":"Pass-Word"}""")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, login.statusCode())
        return client
    }

    private fun req(path: String) = HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type", "application/json")

    private fun body(json: String) = HttpRequest.BodyPublishers.ofString(json)

    private fun createCase(
        client: HttpClient,
        clientName: String,
    ): String {
        val response =
            client.send(
                req("/api/v1/credit-cases")
                    .POST(body("""{"clientName":"$clientName","productType":"LEASING","contractType":"AVEC_CONTRAT","currency":"GNF"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, response.statusCode(), "body=${response.body()}")
        return Regex("""DOS-\d{4}-\d{4}""").find(response.body())!!.value
    }

    @Test
    fun `lists created cases newest-first with status, and paginates`() {
        val client = authenticatedClient()
        val first = createCase(client, "Client Liste A")
        val second = createCase(client, "Client Liste B")
        val third = createCase(client, "Client Liste C")

        val list =
            client.send(
                req("/api/v1/credit-cases?size=50").GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, list.statusCode())
        val listBody = list.body()

        // All three created cases are present, with the phase-1 status exposed.
        assertContains(listBody, first)
        assertContains(listBody, second)
        assertContains(listBody, third)
        assertContains(listBody, "EN_ATTENTE_AMORTISSEMENT")

        // Default order is createdAt descending: the most recent appears before the oldest.
        assertTrue(
            listBody.indexOf(third) < listBody.indexOf(first),
            "expected newest ($third) before oldest ($first) in default ordering",
        )

        // Pagination is exposed and honored.
        val firstPage =
            client.send(
                req("/api/v1/credit-cases?page=0&size=2").GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, firstPage.statusCode())
        assertContains(firstPage.body(), "\"size\":2")
        assertContains(firstPage.body(), "\"hasNext\":true")
    }

    @Test
    fun `rejects listing without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val response =
            anonymous.send(
                req("/api/v1/credit-cases").GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(401, response.statusCode())
    }
}
