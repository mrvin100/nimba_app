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
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AmortizationAnalyticsEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaAnalyticsBoundary"
    private val json = ObjectMapper()

    private fun analystId(): UUID = requireNotNull(seedDriAnalyst(users, passwordEncoder, "analytics@banque.test").id)

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"analytics@banque.test","password":"Pass-Word"}"""))
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
        client.send(
            HttpRequest
                .newBuilder(URI(base(caseId)))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun get(
        client: HttpClient,
        url: String,
    ): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString())

    @Test
    fun `overview returns coherent server-computed figures in one response`() {
        val client = authenticatedClient()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Analytics", ProductType.LEASING, "GNF", analystId())).id
        upload(client, caseId)

        val response = get(client, "${base(caseId)}/overview")

        assertEquals(200, response.statusCode(), "body=${response.body()}")
        val body: JsonNode = json.readTree(response.body())

        // The fixture holds 24 ordinary échéances + 1 VR.
        val summary = body["summary"]
        assertEquals(24, summary["durationMonths"].asInt())
        val status = body["status"]
        assertEquals(25, status["completedPayments"].asInt() + status["remainingPayments"].asInt())

        // Invariant: financed = paid + remaining, all provided by the backend.
        val loan = summary["loanAmount"].decimalValue()
        val paid = summary["paidPrincipal"].decimalValue()
        val remaining = summary["remainingPrincipal"].decimalValue()
        assertEquals(0, loan.compareTo(paid + remaining), "loanAmount must equal paid + remaining")

        // Chart: baseline point (period 0, full principal) + one point per échéance.
        val chart = body["chart"]
        assertEquals(26, chart.size())
        assertEquals(0, chart[0]["period"].asInt())
        assertEquals(0, chart[0]["remainingCapital"].decimalValue().compareTo(loan))
        assertEquals(0, chart[25]["paidCapital"].decimalValue().compareTo(loan))

        // Timeline marker mirrors the settled count.
        assertEquals(status["completedPayments"].asInt(), body["timeline"]["currentPeriod"].asInt())
    }

    @Test
    fun `overview narrows the chart to the requested date range`() {
        val client = authenticatedClient()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Analytics Range", ProductType.LEASING, "GNF", analystId())).id
        upload(client, caseId)

        val full = json.readTree(get(client, "${base(caseId)}/overview").body())
        val start = full["timeline"]["startDate"].asText()
        val narrowed = json.readTree(get(client, "${base(caseId)}/overview?from=$start&to=$start").body())

        assertTrue(narrowed["chart"].size() < full["chart"].size(), "range filter must reduce the chart dataset")
        // Summary figures ignore the range: they always describe the whole schedule.
        assertEquals(
            0,
            full["summary"]["loanAmount"].decimalValue().compareTo(narrowed["summary"]["loanAmount"].decimalValue()),
        )
    }

    @Test
    fun `table is paginated and filterable by payment status`() {
        val client = authenticatedClient()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Analytics Table", ProductType.LEASING, "GNF", analystId())).id
        upload(client, caseId)

        val page = json.readTree(get(client, "${base(caseId)}/table?page=0&size=10").body())
        assertEquals(10, page["content"].size())
        assertEquals(25, page["totalElements"].asLong())
        assertTrue(page["hasNext"].asBoolean())

        val upcoming = json.readTree(get(client, "${base(caseId)}/table?status=A_VENIR&size=100").body())
        assertTrue(upcoming["content"].all { it["status"].asText() == "A_VENIR" })
    }

    @Test
    fun `overview returns 404 when no schedule was imported`() {
        val client = authenticatedClient()
        val caseId = creditCases.createCase(CreateCreditCaseCommand("Sans Échéancier", ProductType.LEASING, "GNF", analystId())).id

        assertEquals(404, get(client, "${base(caseId)}/overview").statusCode())
    }
}
