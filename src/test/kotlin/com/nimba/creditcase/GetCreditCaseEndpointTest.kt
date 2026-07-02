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
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GetCreditCaseEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun analystId(): UUID {
        val existing = users.findByEmail("getcase@banque.test")
        return existing?.id ?: requireNotNull(
            seedDriAnalyst(users, passwordEncoder, "getcase@banque.test").id,
        )
    }

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"getcase@banque.test","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun get(
        client: HttpClient,
        id: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$id")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `returns the case by id with its status`() {
        val created = creditCases.createCase(CreateCreditCaseCommand("Client Get", ProductType.LEASING, "GNF", analystId()))
        val client = authenticatedClient()

        val response = get(client, created.id.toString())

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), created.caseNumber)
        assertContains(response.body(), "EN_ATTENTE_AMORTISSEMENT")
    }

    @Test
    fun `returns 404 for an unknown case`() {
        val response = get(authenticatedClient(), UUID.randomUUID().toString())
        assertEquals(404, response.statusCode())
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val response = get(anonymous, UUID.randomUUID().toString())
        assertEquals(401, response.statusCode())
    }
}
