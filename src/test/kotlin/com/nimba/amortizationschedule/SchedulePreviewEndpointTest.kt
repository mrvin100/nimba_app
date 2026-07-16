package com.nimba.amortizationschedule

import com.nimba.TestcontainersConfiguration
import com.nimba.creditcase.ContractType
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
import java.io.File
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
class SchedulePreviewEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaTestBoundary"

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "preview@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val login =
            client.send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"email":"preview@banque.test","password":"Pass-Word"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, login.statusCode())
        return client
    }

    private fun newCaseId(): UUID =
        creditCases
            .createCase(
                CreateCreditCaseCommand(
                    "Client Preview",
                    ProductType.LEASING,
                    "GNF",
                    analystId(),
                    contractType = ContractType.AVEC_CONTRAT,
                ),
            ).id

    private fun multipartBody(
        filename: String,
        content: ByteArray,
    ): ByteArray {
        val head =
            (
                "--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n" +
                    "Content-Type: text/csv\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        return head + content + tail
    }

    private fun postPreview(
        client: HttpClient,
        caseId: UUID,
        filename: String,
        content: ByteArray,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule/preview"))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(filename, content)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `previews the valid file with no errors and persists nothing`() {
        val client = authenticatedClient()
        val caseId = newCaseId()
        val content = File("docs/examples/exemple-echeancier-valide.csv").readBytes()

        val response = postPreview(client, caseId, "echeancier.csv", content)

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "\"valid\":true")
        // 25 data lines previewed (24 + VR).
        assertEquals(25, Regex("\"lineNumber\"").findAll(response.body()).count())
    }

    @Test
    fun `previews the invalid file with a 200 and surfaces the errors`() {
        val client = authenticatedClient()
        val caseId = newCaseId()
        val content = File("docs/examples/exemple-echeancier-invalide.csv").readBytes()

        val response = postPreview(client, caseId, "echeancier.csv", content)

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "\"valid\":false")
        assertContains(response.body(), "capital")
        assertContains(response.body(), "loyer_ttc")
    }

    @Test
    fun `returns 400 for a fundamentally unreadable file`() {
        val client = authenticatedClient()
        val caseId = newCaseId()

        val response = postPreview(client, caseId, "bad.csv", "a;b;c\n1;2;3".toByteArray())

        assertEquals(400, response.statusCode())
    }

    @Test
    fun `returns 404 for an unknown case`() {
        val client = authenticatedClient()
        val content = "x".toByteArray()

        val response = postPreview(client, UUID.randomUUID(), "echeancier.csv", content)

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `rejects preview without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val caseId = newCaseId()

        val response = postPreview(anonymous, caseId, "echeancier.csv", "x".toByteArray())

        assertEquals(401, response.statusCode())
    }

    @Test
    fun `rejects an oversized file with 413`() {
        val client = authenticatedClient()
        val caseId = newCaseId()
        val tooBig = ByteArray(2 * 1024 * 1024 + 1024) { 'a'.code.toByte() }

        val response = postPreview(client, caseId, "big.csv", tooBig)

        assertEquals(413, response.statusCode())
    }
}
