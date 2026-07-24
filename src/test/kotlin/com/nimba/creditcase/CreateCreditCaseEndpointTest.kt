package com.nimba.creditcase

import com.nimba.TestcontainersConfiguration
import com.nimba.client.ClientModuleApi
import com.nimba.identity.internal.UserRepository
import com.nimba.seedClient
import com.nimba.seedDriAnalyst
import org.junit.jupiter.api.BeforeEach
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
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateCreditCaseEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Value("\${local.server.port}") private val port: Int,
) {
    @BeforeEach
    fun seedAnalyst() {
        seedDriAnalyst(users, passwordEncoder, "creator@banque.test")
    }

    /** Seeds a client (the dossier's required link) named [raisonSociale] and returns its id. */
    private fun clientId(raisonSociale: String): String = seedClient(clients, raisonSociale).toString()

    private fun authenticatedClient(): HttpClient {
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val login =
            client.send(
                request("/api/v1/auth/login").POST(json("""{"email":"creator@banque.test","password":"Pass-Word"}""")).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, login.statusCode(), "precondition: analyst login should succeed")
        return client
    }

    private fun request(path: String) =
        HttpRequest.newBuilder(URI("http://localhost:$port$path")).header("Content-Type", "application/json")

    private fun json(body: String) = HttpRequest.BodyPublishers.ofString(body)

    @Test
    fun `creates a credit case and returns 201 with a generated case number`() {
        val client = authenticatedClient()
        val id = clientId("ETS OC ET FRERES")

        val response =
            client.send(
                request("/api/v1/credit-cases")
                    .POST(
                        json(
                            """{"clientId":"$id","productType":"LEASING","contractType":"AVEC_CONTRAT",""" +
                                """"currency":"GNF","accountNumber":"0102386501-90"}""",
                        ),
                    ).build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(201, response.statusCode(), "body=${response.body()}")
        assertContains(response.body(), "ETS OC ET FRERES")
        assertContains(response.body(), "0102386501-90")
        assertTrue(Regex("""DOS-\d{4}-\d{4}""").containsMatchIn(response.body()), "expected a case number; body=${response.body()}")
    }

    @Test
    fun `creates an MC2_MUFFA case without a contract type`() {
        val client = authenticatedClient()
        val id = clientId("MC2 Client")

        val response =
            client.send(
                request("/api/v1/credit-cases")
                    .POST(json("""{"clientId":"$id","productType":"MC2_MUFFA","currency":"GNF"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(201, response.statusCode(), "body=${response.body()}")
        assertContains(response.body(), "MC2_MUFFA")
    }

    @Test
    fun `rejects a LEASING case without a contract type`() {
        val client = authenticatedClient()
        val id = clientId("Client Sans Type")

        val response =
            client.send(
                request("/api/v1/credit-cases")
                    .POST(json("""{"clientId":"$id","productType":"LEASING","currency":"GNF"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `rejects an MC2_MUFFA case carrying a contract type`() {
        val client = authenticatedClient()
        val id = clientId("Client Incohérent")

        val response =
            client.send(
                request("/api/v1/credit-cases")
                    .POST(
                        json(
                            """{"clientId":"$id","productType":"MC2_MUFFA","contractType":"AVEC_CONTRAT",""" +
                                """"currency":"GNF"}""",
                        ),
                    ).build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `rejects creation without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

        val response =
            anonymous.send(
                request("/api/v1/credit-cases")
                    .POST(json("""{"clientId":"${UUID.randomUUID()}","productType":"LEASING","currency":"GNF"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(401, response.statusCode())
    }

    @Test
    fun `rejects creation for an unknown client with 404`() {
        val client = authenticatedClient()

        val response =
            client.send(
                request("/api/v1/credit-cases")
                    .POST(json("""{"clientId":"${UUID.randomUUID()}","productType":"MC2_MUFFA","currency":"GNF"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(404, response.statusCode())
    }
}
