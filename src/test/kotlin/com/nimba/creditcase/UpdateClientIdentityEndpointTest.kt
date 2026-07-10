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
class UpdateClientIdentityEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "identity@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"identity@banque.test","password":"Pass-Word"}"""))
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
                .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$id/identity"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `captures the client identity and returns it on the case`() {
        val created = creditCases.createCase(CreateCreditCaseCommand("Client Identité", ProductType.MC2_MUFFA, "GNF", analystId()))
        val client = authenticatedClient()

        val response =
            put(
                client,
                created.id.toString(),
                """{"formeJuridique":"SARL","dateCreation":"2020-01-15","adressePhysique":"Kaloum, Conakry",
                    "activiteDeBase":"Transport","codeNif":"123456","principalDirigeant":"Mamadou Diallo",
                    "dateEntreeRelation":"2019-06-01","dateDerniereVisite":"2026-06-10","agence":"KALOUM",
                    "gestionnaire":"Emile Traoré","analyste":"Souwla Soumaoro","cotationPrecedente":"MED",
                    "cotationActuelle":"BON"}""",
            )

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "\"formeJuridique\":\"SARL\"")
        assertContains(response.body(), "\"dateCreation\":\"2020-01-15\"")
        assertContains(response.body(), "\"agence\":\"KALOUM\"")
        assertContains(response.body(), "\"cotationActuelle\":\"BON\"")

        val fetched =
            client.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/credit-cases/${created.id}")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertContains(fetched.body(), "\"principalDirigeant\":\"Mamadou Diallo\"")
    }

    @Test
    fun `a new case starts with no identity captured`() {
        val created = creditCases.createCase(CreateCreditCaseCommand("Client Vierge", ProductType.MC2_MUFFA, "GNF", analystId()))
        val client = authenticatedClient()

        val response =
            client.send(
                HttpRequest.newBuilder(URI("http://localhost:$port/api/v1/credit-cases/${created.id}")).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertContains(response.body(), "\"formeJuridique\":null")
    }

    @Test
    fun `rejects an overlong field`() {
        val created = creditCases.createCase(CreateCreditCaseCommand("Client Long", ProductType.MC2_MUFFA, "GNF", analystId()))
        val client = authenticatedClient()

        val response = put(client, created.id.toString(), """{"formeJuridique":"${"A".repeat(101)}"}""")

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val response = put(anonymous, UUID.randomUUID().toString(), """{"formeJuridique":"SARL"}""")
        assertEquals(401, response.statusCode())
    }
}
