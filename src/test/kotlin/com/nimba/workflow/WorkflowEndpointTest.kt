package com.nimba.workflow

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.seedMember
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import java.math.BigDecimal
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkflowEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun member(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

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

    private fun post(
        client: HttpClient,
        path: String,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun get(
        client: HttpClient,
        path: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun submittedDossier(driId: UUID): UUID {
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand("Client HTTP", ProductType.LEASING, "GNF", driId, contractType = ContractType.AVEC_CONTRAT),
                ).id
        val line =
            AmortizationScheduleLine(
                numeroEcheance = "1",
                dateEcheance = LocalDate.of(2026, 5, 1),
                interet = BigDecimal("100.0000"),
                equipement = BigDecimal("900.0000"),
                assurance = BigDecimal.ZERO,
                tracking = BigDecimal.ZERO,
                immatriculation = BigDecimal.ZERO,
                capital = BigDecimal("900.0000"),
                loyerHt = BigDecimal("1000.0000"),
                taxes = BigDecimal.ZERO,
                loyerTtc = BigDecimal("1000.0000"),
                capitalRestantDu = BigDecimal.ZERO,
            )
        schedules.saveAndFlush(AmortizationSchedule(caseId, 1, "echeancier.csv", driId).apply { addLine(line) })
        analysisSheets.create(CreateAnalysisSheetCommand(caseId, driId))
        analysisSheets.publish(caseId)
        return caseId
    }

    @Test
    fun `DRI submits, DCM approves, and the timeline reflects both`() {
        val driId = member("http-dri@banque.test", Department.DRI)
        member("http-dcm@banque.test", Department.DCM)
        val caseId = submittedDossier(driId)

        val dri = clientFor("http-dri@banque.test")
        val submit = post(dri, "/credit-cases/$caseId/workflow/actions", """{"action":"SUBMIT"}""")
        assertEquals(200, submit.statusCode(), submit.body())
        assertContains(submit.body(), "EN_REVUE_DCM")

        val dcm = clientFor("http-dcm@banque.test")
        // A DCM reviewer can read the dossier it must judge (security widened to reviewers).
        assertEquals(200, get(dcm, "/credit-cases/$caseId").statusCode())

        val approve = post(dcm, "/credit-cases/$caseId/workflow/actions", """{"action":"APPROVE","comment":"OK"}""")
        assertEquals(200, approve.statusCode(), approve.body())
        assertContains(approve.body(), "EN_REVUE_DRC")

        val state = get(dcm, "/credit-cases/$caseId/workflow")
        assertContains(state.body(), "SUBMIT")
        assertContains(state.body(), "APPROVE")
    }

    @Test
    fun `a DRI member cannot act at the DCM stage`() {
        val driId = member("http-dri2@banque.test", Department.DRI)
        val caseId = submittedDossier(driId)
        val dri = clientFor("http-dri2@banque.test")
        post(dri, "/credit-cases/$caseId/workflow/actions", """{"action":"SUBMIT"}""")

        val forbidden = post(dri, "/credit-cases/$caseId/workflow/actions", """{"action":"APPROVE"}""")
        assertEquals(403, forbidden.statusCode())
    }

    @Test
    fun `the DCM queue lists a submitted dossier`() {
        val driId = member("http-dri3@banque.test", Department.DRI)
        member("http-dcm3@banque.test", Department.DCM)
        val caseId = submittedDossier(driId)
        val dri = clientFor("http-dri3@banque.test")
        post(dri, "/credit-cases/$caseId/workflow/actions", """{"action":"SUBMIT"}""")

        val queue = get(clientFor("http-dcm3@banque.test"), "/workflow/queue")
        assertEquals(200, queue.statusCode())
        assertContains(queue.body(), caseId.toString())
    }
}
