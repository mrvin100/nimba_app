package com.nimba.pv

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
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.internal.WorkflowService
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
class PvEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val workflow: WorkflowService,
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

    private fun put(
        client: HttpClient,
        path: String,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1$path"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
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

    /** A dossier driven all the way to APPROUVE via the workflow service directly (HTTP setup is covered by WorkflowEndpointTest). */
    private fun approvedDossier(driId: UUID): UUID {
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand("Client PV HTTP", ProductType.LEASING, "GNF", driId, contractType = ContractType.AVEC_CONTRAT),
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

        val dcm = member("pvhttp-dcm-${UUID.randomUUID()}@banque.test", Department.DCM)
        val drc = member("pvhttp-drc-${UUID.randomUUID()}@banque.test", Department.DRC)
        val comite1 = member("pvhttp-comite1-${UUID.randomUUID()}@banque.test", Department.COMITE)
        val comite2 = member("pvhttp-comite2-${UUID.randomUUID()}@banque.test", Department.COMITE)
        workflow.act(caseId, driId, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)
        workflow.act(caseId, drc, WorkflowAction.APPROVE, null)
        workflow.act(caseId, comite1, WorkflowAction.APPROVE, null)
        workflow.act(caseId, comite2, WorkflowAction.APPROVE, null)
        return caseId
    }

    @Test
    fun `a DCM member creates, edits and finalizes the PV`() {
        val driId = member("pv-http-dri@banque.test", Department.DRI)
        val dcmEmail = "pv-http-dcm@banque.test"
        member(dcmEmail, Department.DCM)
        val caseId = approvedDossier(driId)
        val dcm = clientFor(dcmEmail)

        val created = post(dcm, "/credit-cases/$caseId/pv", """{"seanceDate":"2026-07-13"}""")
        assertEquals(201, created.statusCode(), created.body())
        assertContains(created.body(), "\"status\":\"DRAFT\"")

        val updated =
            put(
                dcm,
                "/credit-cases/$caseId/pv",
                """{"seanceDate":"2026-07-14","rapporteur":"Souwla Soumaoro","president":"Emile Traoré",
                    "pointsForts":"Client fidèle","pointsFaibles":"Trésorerie tendue",
                    "debats":[{"preoccupation":"Retard passé","reponse":"Régularisé","recommandation":"Favorable"}]}""",
            )
        assertEquals(200, updated.statusCode(), updated.body())
        assertContains(updated.body(), "Souwla Soumaoro")

        val finalized = post(dcm, "/credit-cases/$caseId/pv/finalize", "")
        assertEquals(200, finalized.statusCode(), finalized.body())
        assertContains(finalized.body(), "\"status\":\"FINAL\"")
        assertContains(finalized.body(), "\"snapshot\"")

        val lockedEdit = put(dcm, "/credit-cases/$caseId/pv", """{"seanceDate":"2026-07-15","debats":[]}""")
        assertEquals(409, lockedEdit.statusCode())
    }

    @Test
    fun `a DRI member can read but not generate the PV`() {
        val driId = member("pv-http-dri2@banque.test", Department.DRI)
        val caseId = approvedDossier(driId)
        val dri = clientFor("pv-http-dri2@banque.test")

        val forbidden = post(dri, "/credit-cases/$caseId/pv", """{"seanceDate":"2026-07-13"}""")
        assertEquals(403, forbidden.statusCode())

        val notFound = get(dri, "/credit-cases/$caseId/pv")
        assertEquals(404, notFound.statusCode())
    }

    @Test
    fun `rejects generating a PV before the comite approves the dossier`() {
        val driId = member("pv-http-dri3@banque.test", Department.DRI)
        member("pv-http-dcm3@banque.test", Department.DCM)
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand("Trop tôt HTTP", ProductType.LEASING, "GNF", driId, contractType = ContractType.AVEC_CONTRAT),
                ).id
        val dcm = clientFor("pv-http-dcm3@banque.test")

        val response = post(dcm, "/credit-cases/$caseId/pv", """{"seanceDate":"2026-07-13"}""")
        assertEquals(409, response.statusCode())
    }
}
