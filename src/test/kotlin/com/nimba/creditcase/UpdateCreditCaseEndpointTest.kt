package com.nimba.creditcase

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
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UpdateCreditCaseEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun analystId(): UUID =
        users.findByEmail("updatecase@banque.test")?.id
            ?: requireNotNull(
                users.saveAndFlush(User("Analyste Update", "updatecase@banque.test", passwordEncoder.encode("Pass-Word"))).id,
            )

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"updatecase@banque.test","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun put(
        client: HttpClient,
        id: String,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$id"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `updates the case general information, keeping its number`() {
        val created = creditCases.createCase(CreateCreditCaseCommand("Ancien Client", ProductType.LEASING, "GNF", analystId()))
        val client = authenticatedClient()

        val response = put(client, created.id.toString(), """{"clientName":"Nouveau Client","productType":"LEASING","currency":"USD"}""")

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "Nouveau Client")
        assertContains(response.body(), "USD")
        assertContains(response.body(), created.caseNumber)
        assertFalse(response.body().contains("Ancien Client"))
    }

    @Test
    fun `returns 404 for an unknown case`() {
        val response =
            put(
                authenticatedClient(),
                UUID.randomUUID().toString(),
                """{"clientName":"X","productType":"LEASING","currency":"GNF"}""",
            )
        assertEquals(404, response.statusCode())
    }

    @Test
    fun `rejects an invalid currency`() {
        val created = creditCases.createCase(CreateCreditCaseCommand("Client", ProductType.LEASING, "GNF", analystId()))

        val response =
            put(authenticatedClient(), created.id.toString(), """{"clientName":"Client","productType":"LEASING","currency":"gnf"}""")

        assertEquals(400, response.statusCode(), response.body())
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val response = put(anonymous, UUID.randomUUID().toString(), """{"clientName":"X","productType":"LEASING","currency":"GNF"}""")
        assertEquals(401, response.statusCode())
    }
}
