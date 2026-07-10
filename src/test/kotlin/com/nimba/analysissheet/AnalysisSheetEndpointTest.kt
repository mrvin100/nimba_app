package com.nimba.analysissheet

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

@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AnalysisSheetEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaAnalysisSheetBoundary"

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "fa@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val login =
            client.send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"email":"fa@banque.test","password":"Pass-Word"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, login.statusCode())
        return client
    }

    private fun newCaseId(): UUID =
        creditCases
            .createCase(
                CreateCreditCaseCommand("Client FA", ProductType.LEASING, "GNF", analystId(), contractType = ContractType.AVEC_CONTRAT),
            ).id

    private fun uploadSchedule(
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
        val response =
            client.send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule"))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, response.statusCode(), "precondition: schedule upload should succeed; body=${response.body()}")
    }

    private fun path(caseId: UUID) = "http://localhost:$port/api/v1/credit-cases/$caseId/analysis-sheet"

    @Test
    fun `initiates, edits and publishes the FA, prefilled from the TA`() {
        val client = authenticatedClient()
        val caseId = newCaseId()
        uploadSchedule(client, caseId)

        val created =
            client.send(
                HttpRequest.newBuilder(URI(path(caseId))).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, created.statusCode(), created.body())
        assertContains(created.body(), "\"status\":\"DRAFT\"")
        assertContains(created.body(), "\"faVariant\":\"LEASING_AVEC_CONTRAT\"")
        assertContains(created.body(), "\"taSummary\"")

        val updated =
            client.send(
                HttpRequest
                    .newBuilder(URI(path(caseId)))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("""{"content":"Notes d'analyse"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, updated.statusCode(), updated.body())
        assertContains(updated.body(), "Notes d'analyse")

        val published =
            client.send(
                HttpRequest.newBuilder(URI("${path(caseId)}/publish")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, published.statusCode(), published.body())
        assertContains(published.body(), "\"status\":\"PUBLISHED\"")

        val lockedEdit =
            client.send(
                HttpRequest
                    .newBuilder(URI(path(caseId)))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString("""{"content":"trop tard"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(409, lockedEdit.statusCode())
    }

    @Test
    fun `rejects initiating the FA before the TA is uploaded`() {
        val client = authenticatedClient()
        val caseId = newCaseId()

        val response =
            client.send(
                HttpRequest.newBuilder(URI(path(caseId))).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(409, response.statusCode())
    }

    @Test
    fun `returns 404 for a case without an FA yet`() {
        val client = authenticatedClient()
        val caseId = newCaseId()

        val response = client.send(HttpRequest.newBuilder(URI(path(caseId))).GET().build(), HttpResponse.BodyHandlers.ofString())

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val caseId = newCaseId()

        val response =
            anonymous.send(
                HttpRequest.newBuilder(URI(path(caseId))).GET().build(),
                HttpResponse.BodyHandlers.ofString(),
            )

        assertEquals(401, response.statusCode())
    }
}
