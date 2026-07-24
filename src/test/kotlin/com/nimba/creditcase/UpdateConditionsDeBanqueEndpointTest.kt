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
class UpdateConditionsDeBanqueEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "conditions@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"conditions@banque.test","password":"Pass-Word"}"""))
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
                .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$id/conditions-banque"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `captures the conditions de banque and returns them on the case`() {
        val created =
            creditCases.createCase(
                CreateCreditCaseCommand(com.nimba.seedClient(clients, "Client Conditions"), ProductType.MC2_MUFFA, "GNF", analystId()),
            )
        val client = authenticatedClient()

        val response =
            put(
                client,
                created.id.toString(),
                """{"tauxInteretPct":9.5,"fraisMiseEnPlacePct":1.5,"comEngagementPct":0.5,"fraisEtudesPct":1,
                    "valeurResiduellePct":2,"fraisDivers":"[{\"label\":\"Notification\",\"montant\":50000}]"}""",
            )

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "\"tauxInteretPct\":9.5")
        assertContains(response.body(), "\"comEngagementPct\":0.5")
        assertContains(response.body(), "\"valeurResiduellePct\":2")
        assertContains(response.body(), "Notification")

        val fetched =
            client.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/credit-cases/${created.id}")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertContains(fetched.body(), "\"fraisEtudesPct\":1")
    }

    @Test
    fun `a new case starts with no conditions de banque captured`() {
        val created =
            creditCases.createCase(
                CreateCreditCaseCommand(com.nimba.seedClient(clients, "Client Vierge"), ProductType.MC2_MUFFA, "GNF", analystId()),
            )
        val client = authenticatedClient()

        val response =
            client.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/credit-cases/${created.id}")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertContains(response.body(), "\"tauxInteretPct\":null")
    }

    @Test
    fun `rejects an invalid percentage`() {
        val created =
            creditCases.createCase(
                CreateCreditCaseCommand(com.nimba.seedClient(clients, "Client Invalide"), ProductType.MC2_MUFFA, "GNF", analystId()),
            )
        val client = authenticatedClient()

        val response = put(client, created.id.toString(), """{"tauxInteretPct":12345.6789}""")

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val response = put(anonymous, UUID.randomUUID().toString(), """{"tauxInteretPct":9.5}""")
        assertEquals(401, response.statusCode())
    }
}
