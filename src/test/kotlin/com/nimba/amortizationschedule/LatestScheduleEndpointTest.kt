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
import org.springframework.test.context.TestPropertySource
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The case's schedule-state endpoint (GET the resource): the échéancier screen
 * derives its workflow from it — nothing imported (404), imported but trades not
 * generated from this version (tradesUpToDate=false, right after an upload or a
 * re-import), and generated (true). Server state, so a page refresh cannot lose
 * the "imported, generate the trades" step.
 */
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LatestScheduleEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaLatestScheduleBoundary"
    private val json = ObjectMapper()

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "etat-echeancier@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"etat-echeancier@banque.test","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun base(caseId: UUID) = "http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule"

    private fun upload(
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
                    .newBuilder(URI(base(caseId)))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, response.statusCode(), "upload failed: ${response.body()}")
    }

    private fun latest(
        client: HttpClient,
        caseId: UUID,
    ): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI(base(caseId))).GET().build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun `schedule state follows the upload, generate, re-import workflow`() {
        val client = authenticatedClient()
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "État Échéancier"),
                        ProductType.LEASING,
                        "GNF",
                        analystId(),
                        contractType = ContractType.AVEC_CONTRAT,
                    ),
                ).id

        // Nothing imported yet: same 404 semantics as the analytics endpoints.
        assertEquals(404, latest(client, caseId).statusCode())

        // Imported, trades not generated yet — the state a refresh must not lose.
        upload(client, caseId)
        var body: JsonNode = json.readTree(latest(client, caseId).body())
        assertEquals(1, body["versionNumber"].asInt())
        assertEquals(25, body["lineCount"].asInt())
        assertFalse(body["tradesUpToDate"].asBoolean())

        // Generated from this version.
        val generate =
            client.send(
                HttpRequest.newBuilder(URI("${base(caseId)}/trades")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, generate.statusCode(), "body=${generate.body()}")
        body = json.readTree(latest(client, caseId).body())
        assertTrue(body["tradesUpToDate"].asBoolean())

        // A re-import supersedes the generated trades' source: regenerate.
        upload(client, caseId)
        body = json.readTree(latest(client, caseId).body())
        assertEquals(2, body["versionNumber"].asInt())
        assertFalse(body["tradesUpToDate"].asBoolean())
    }
}
