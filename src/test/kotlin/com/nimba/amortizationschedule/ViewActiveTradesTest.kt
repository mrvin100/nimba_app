package com.nimba.amortizationschedule

import com.nimba.TestcontainersConfiguration
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import java.io.File
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ViewActiveTradesTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaViewBoundary"

    private fun analystId(): UUID {
        val existing = users.findByEmail("view@banque.test")
        return existing?.id ?: requireNotNull(
            users.saveAndFlush(User("Analyste View", "view@banque.test", requireNotNull(passwordEncoder.encode("Pass-Word")))).id,
        )
    }

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"view@banque.test","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun tradesUri(caseId: UUID) = URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule/trades")

    private fun uploadValid(
        client: HttpClient,
        caseId: UUID,
    ) {
        val content = File("docs/examples/exemple-echeancier-valide.csv").readBytes()
        val head =
            (
                "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"echeancier.csv\"\r\n" +
                    "Content-Type: text/csv\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val body = head + content + "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule"))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun generate(
        client: HttpClient,
        caseId: UUID,
    ) = client.send(
        HttpRequest.newBuilder(tradesUri(caseId)).POST(HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString(),
    )

    private fun list(
        client: HttpClient,
        caseId: UUID,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(tradesUri(caseId)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `returns an empty list when no trades have been generated`() {
        val client = authenticatedClient()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Vide", ProductType.LEASING, "GNF", analystId())).id

        val response = list(client, caseId)

        assertEquals(200, response.statusCode())
        assertEquals("[]", response.body().trim())
    }

    @Test
    fun `returns only the latest generation after a regeneration`() {
        val client = authenticatedClient()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("ETS OC ET FRERES", ProductType.LEASING, "GNF", analystId())).id

        uploadValid(client, caseId)
        generate(client, caseId)
        uploadValid(client, caseId)
        generate(client, caseId)

        val response = list(client, caseId)

        assertEquals(200, response.statusCode())
        assertEquals(25, Regex("\"dueDate\"").findAll(response.body()).count())
    }

    @Test
    fun `rejects consultation without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Anon", ProductType.LEASING, "GNF", analystId())).id

        val response = list(anonymous, caseId)

        assertEquals(401, response.statusCode())
    }
}
