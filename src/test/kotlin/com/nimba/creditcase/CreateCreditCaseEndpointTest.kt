package com.nimba.creditcase

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.UserRepository
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreateCreditCaseEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Value("\${local.server.port}") private val port: Int,
) {
    @BeforeEach
    fun seedAnalyst() {
        seedDriAnalyst(users, passwordEncoder, "creator@banque.test")
    }

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

        val response =
            client.send(
                request("/api/v1/credit-cases")
                    .POST(
                        json(
                            """{"clientName":"ETS OC ET FRERES","productType":"LEASING","currency":"GNF","accountNumber":"0102386501-90"}""",
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
    fun `rejects creation without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()

        val response =
            anonymous.send(
                request("/api/v1/credit-cases")
                    .POST(json("""{"clientName":"Client X","productType":"LEASING","currency":"GNF"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(401, response.statusCode())
    }

    @Test
    fun `rejects a blank client name with 400`() {
        val client = authenticatedClient()

        val response =
            client.send(
                request("/api/v1/credit-cases")
                    .POST(json("""{"clientName":"","productType":"LEASING","currency":"GNF"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(400, response.statusCode())
    }
}
