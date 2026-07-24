package com.nimba.creditcase

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.amortizationschedule.internal.TradeRepository
import com.nimba.identity.internal.UserRepository
import com.nimba.seedDriAnalyst
import com.nimba.seedPlatformAdmin
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
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
 * Administrative acts on a dossier (NIMBA-45): archive / restore / definitive
 * deletion, restricted to ROLE_ADMIN, with the archived filter on the list and
 * the cross-module purge of schedules and trades on deletion.
 */
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CreditCaseAdminActionsTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val trades: TradeRepository,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaAdminActionsBoundary"
    private val json = ObjectMapper()

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "actions-analyste@banque.test").id)

    private fun clientFor(email: String): HttpClient {
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun analystClient(): HttpClient {
        analystId()
        return clientFor("actions-analyste@banque.test")
    }

    private fun adminClient(): HttpClient {
        seedPlatformAdmin(users, passwordEncoder, "actions-admin@banque.test")
        return clientFor("actions-admin@banque.test")
    }

    private fun url(path: String) = "http://localhost:$port/api/v1$path"

    private fun post(
        client: HttpClient,
        path: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI(url(path))).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(
        client: HttpClient,
        path: String,
    ): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI(url(path))).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun delete(
        client: HttpClient,
        path: String,
    ): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI(url(path))).DELETE().build(), HttpResponse.BodyHandlers.ofString())

    private fun newCase(name: String): UUID =
        creditCases
            .createCase(
                CreateCreditCaseCommand(
                    com.nimba.seedClient(clients, name),
                    ProductType.LEASING,
                    "GNF",
                    analystId(),
                    contractType = ContractType.AVEC_CONTRAT,
                ),
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
                    .newBuilder(URI(url("/credit-cases/$caseId/amortization-schedule")))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, response.statusCode(), "upload failed: ${response.body()}")
    }

    @Test
    fun `admin archives a case out of the active list and restores it`() {
        val admin = adminClient()
        val analyst = analystClient()
        val caseId = newCase("Dossier à archiver")

        val archived = post(admin, "/credit-cases/$caseId/archive")
        assertEquals(200, archived.statusCode(), "body=${archived.body()}")
        assertFalse(json.readTree(archived.body())["archivedAt"].isNull)

        fun listIds(filter: String): List<String> =
            json.readTree(get(analyst, "/credit-cases?size=100$filter").body())["content"].toList().map { it["id"].asText() }

        assertFalse(listIds("&archived=false").contains(caseId.toString()), "an archived case must leave the active list")
        assertTrue(listIds("&archived=true").contains(caseId.toString()))
        assertTrue(listIds("").contains(caseId.toString()), "the unfiltered list keeps every case")

        assertEquals(200, post(admin, "/credit-cases/$caseId/unarchive").statusCode())
        assertTrue(listIds("&archived=false").contains(caseId.toString()))
    }

    @Test
    fun `deletion purges the case with its schedules and trades`() {
        val admin = adminClient()
        val analyst = analystClient()
        val caseId = newCase("Dossier à supprimer")
        uploadSchedule(analyst, caseId)
        assertEquals(201, post(analyst, "/credit-cases/$caseId/amortization-schedule/trades").statusCode())

        val response = delete(admin, "/credit-cases/$caseId")
        assertEquals(204, response.statusCode(), "body=${response.body()}")

        assertEquals(404, get(analyst, "/credit-cases/$caseId").statusCode())
        assertTrue(schedules.findByCreditCaseId(caseId).isEmpty(), "schedules must be purged with the case")
        assertTrue(trades.findByCreditCaseId(caseId).isEmpty(), "trades must be purged with the case")
    }

    @Test
    fun `the administrative actions are closed to a DRI analyst`() {
        val analyst = analystClient()
        val caseId = newCase("Dossier protégé")

        assertEquals(403, post(analyst, "/credit-cases/$caseId/archive").statusCode())
        assertEquals(403, delete(analyst, "/credit-cases/$caseId").statusCode())
    }

    @Test
    fun `an administrator without DRI membership reaches only the administrative actions`() {
        val admin = adminClient()
        val caseId = newCase("Dossier admin seul")

        assertEquals(403, get(admin, "/credit-cases").statusCode(), "the DRI business surface stays closed to a pure admin")
        assertEquals(200, post(admin, "/credit-cases/$caseId/archive").statusCode())
    }
}
