package com.nimba.amortizationschedule

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.amortizationschedule.internal.TradeRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that regenerating trades after uploading a corrected schedule version
 * supersedes the previous generation rather than mixing them (NIMBA-24): the new
 * trades become active and the old ones remain in the database, inactive, tied to
 * their original schedule version.
 */
@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeRegenerationTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val trades: TradeRepository,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaRegenBoundary"

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "regen@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"regen@banque.test","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

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
        val response =
            client.send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule"))
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, response.statusCode(), response.body())
    }

    private fun generate(
        client: HttpClient,
        caseId: UUID,
    ) {
        val response =
            client.send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule/trades"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(201, response.statusCode(), response.body())
    }

    @Test
    fun `regeneration on a new version supersedes the previous trades but keeps them`() {
        val client = authenticatedClient()
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        "ETS OC ET FRERES",
                        ProductType.LEASING,
                        "GNF",
                        analystId(),
                        contractType = ContractType.AVEC_CONTRAT,
                    ),
                ).id

        uploadValid(client, caseId)
        val v1Id = requireNotNull(schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(caseId)).id
        generate(client, caseId)

        uploadValid(client, caseId)
        val v2Id = requireNotNull(schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(caseId)).id
        generate(client, caseId)

        val all = trades.findByCreditCaseId(caseId)
        val active = all.filter { it.active }
        val superseded = all.filterNot { it.active }

        assertEquals(50, all.size, "both generations are retained")
        assertEquals(25, active.size)
        assertEquals(25, superseded.size)
        assertTrue(active.all { it.scheduleId == v2Id }, "active trades must come from the latest version")
        assertTrue(superseded.all { it.scheduleId == v1Id }, "superseded trades must come from the first version")

        // The standard active-trades view returns only the second generation.
        assertEquals(25, trades.findByCreditCaseIdAndActiveIsTrue(caseId).size)
    }
}
