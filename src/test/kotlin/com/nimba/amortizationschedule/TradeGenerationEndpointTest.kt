package com.nimba.amortizationschedule

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.TradeRepository
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.CreditCaseStatus
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
import java.math.BigDecimal
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TradeGenerationEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val trades: TradeRepository,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaGenBoundary"

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "gen@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val login =
            client.send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"email":"gen@banque.test","password":"Pass-Word"}"""))
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
                    "ETS OC ET FRERES",
                    ProductType.LEASING,
                    "GNF",
                    analystId(),
                    contractType = ContractType.AVEC_CONTRAT,
                ),
            ).id

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
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule/trades"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `generates the 25 real-case trades with exact dates, amounts and words`() {
        val client = authenticatedClient()
        val caseId = newCaseId()
        uploadValid(client, caseId)

        val response = generate(client, caseId)
        assertEquals(201, response.statusCode(), response.body())

        val active = trades.findByCreditCaseIdAndActiveIsTrueOrderByDueDateAsc(caseId)
        assertEquals(25, active.size)

        val first = active.first { it.numeroEcheance == "1" }
        assertEquals(LocalDate.of(2026, 6, 5), first.dueDate)
        assertEquals(0, first.amount.compareTo(BigDecimal("539571123")))
        assertEquals(
            "Cinq Cent Trente-Neuf Millions Cinq Cent Soixante-Onze Mille Cent Vingt-Trois Francs Guinéens",
            first.amountInWords,
        )

        val vr = active.first { it.numeroEcheance.equals("VR", ignoreCase = true) }
        assertEquals(LocalDate.of(2028, 6, 5), vr.dueDate)
        assertEquals(0, vr.amount.compareTo(BigDecimal("54280000")))
        assertEquals("Cinquante-Quatre Millions Deux Cent Quatre-Vingt Mille Francs Guinéens", vr.amountInWords)

        // The case status flips to TRADES_GENERES.
        assertEquals(CreditCaseStatus.TRADES_GENERES, creditCases.findById(caseId)?.status)
    }

    @Test
    fun `returns 409 when no schedule has been uploaded`() {
        val client = authenticatedClient()
        val caseId = newCaseId()

        val response = generate(client, caseId)

        assertEquals(409, response.statusCode())
    }

    @Test
    fun `rejects generation without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val caseId = newCaseId()

        val response = generate(anonymous, caseId)

        assertEquals(401, response.statusCode())
    }
}
