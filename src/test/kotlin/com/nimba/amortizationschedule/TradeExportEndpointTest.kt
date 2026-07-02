package com.nimba.amortizationschedule

import com.nimba.TestcontainersConfiguration
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.internal.UserRepository
import com.nimba.seedDriAnalyst
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
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeExportEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaExportBoundary"

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "export@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"export@banque.test","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun base(caseId: UUID) = "http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule"

    private fun uploadValidAndGenerate(
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
                .newBuilder(URI(base(caseId)))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        client.send(
            HttpRequest.newBuilder(URI("${base(caseId)}/trades")).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun export(
        client: HttpClient,
        caseId: UUID,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("${base(caseId)}/trades/export")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `exports the active trades as a downloadable CSV`() {
        val client = authenticatedClient()
        val created = creditCases.createCase(CreateCreditCaseCommand("ETS OC ET FRERES", ProductType.LEASING, "GNF", analystId()))
        uploadValidAndGenerate(client, created.id)

        val response = export(client, created.id)

        assertEquals(200, response.statusCode())
        val disposition = response.headers().firstValue("Content-Disposition").orElse("")
        assertContains(disposition, "attachment")
        assertContains(disposition, created.caseNumber)
        // Header + one row per active trade.
        assertContains(response.body(), "numero_echeance;date_echeance;montant_chiffres;montant_lettres;devise")
        assertEquals(25, Regex(";GNF").findAll(response.body()).count())
        assertContains(response.body(), "539571123")
    }

    @Test
    fun `exports the active trades as a downloadable Word document`() {
        val client = authenticatedClient()
        val created = creditCases.createCase(CreateCreditCaseCommand("ETS OC ET FRERES", ProductType.LEASING, "GNF", analystId()))
        uploadValidAndGenerate(client, created.id)

        val response =
            client.send(
                HttpRequest.newBuilder(URI("${base(created.id)}/trades/export/docx")).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray(),
            )

        assertEquals(200, response.statusCode())
        assertContains(response.headers().firstValue("Content-Disposition").orElse(""), ".docx")
        assertContains(response.headers().firstValue("Content-Type").orElse(""), "wordprocessingml")
        val body = response.body()
        assertTrue(body.size > 1000, "the .docx should contain the traités")
        // A .docx is a ZIP archive: it begins with the "PK" signature.
        assertEquals('P'.code.toByte(), body[0])
        assertEquals('K'.code.toByte(), body[1])
    }

    @Test
    fun `returns 404 when there are no active trades to export`() {
        val client = authenticatedClient()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Sans Trades", ProductType.LEASING, "GNF", analystId())).id

        val response = export(client, caseId)

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `rejects export without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Anon", ProductType.LEASING, "GNF", analystId())).id

        val response = export(anonymous, caseId)

        assertTrue(response.statusCode() == 401, "expected 401 but was ${response.statusCode()}")
    }
}
