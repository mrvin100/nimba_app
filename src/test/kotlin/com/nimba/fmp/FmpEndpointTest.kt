package com.nimba.fmp

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
import com.nimba.pv.CreatePvCommand
import com.nimba.pv.PvModuleApi
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
class FmpEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val workflow: WorkflowService,
    @Autowired private val pvs: PvModuleApi,
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

    private fun dossierWithFinalPv(driId: UUID): UUID {
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "Client FMP HTTP"),
                        ProductType.LEASING,
                        "GNF",
                        driId,
                        contractType = ContractType.AVEC_CONTRAT,
                    ),
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

        val dcm = member("fmphttp-dcm-${UUID.randomUUID()}@banque.test", Department.DCM)
        val drc = member("fmphttp-drc-${UUID.randomUUID()}@banque.test", Department.DRC)
        val comite1 = member("fmphttp-comite1-${UUID.randomUUID()}@banque.test", Department.COMITE)
        val comite2 = member("fmphttp-comite2-${UUID.randomUUID()}@banque.test", Department.COMITE)
        workflow.act(caseId, driId, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)
        workflow.act(caseId, drc, WorkflowAction.APPROVE, null)
        workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null)
        workflow.act(caseId, comite1, WorkflowAction.APPROVE, null)
        workflow.act(caseId, comite2, WorkflowAction.APPROVE, null)
        pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 7, 13)))
        pvs.finalize(caseId)
        return caseId
    }

    @Test
    fun `a DCM member generates the FMP from the finalized PV`() {
        val driId = member("fmp-http-dri@banque.test", Department.DRI)
        val dcmEmail = "fmp-http-dcm@banque.test"
        member(dcmEmail, Department.DCM)
        val caseId = dossierWithFinalPv(driId)
        val dcm = clientFor(dcmEmail)

        val created = post(dcm, "/credit-cases/$caseId/fmp", """{"numeroPret":"PRET-2026-001","garantieRef":"GAR-001"}""")
        assertEquals(201, created.statusCode(), created.body())
        assertContains(created.body(), "\"numeroPret\":\"PRET-2026-001\"")
        assertContains(created.body(), "\"gfcEnCharge\"")

        val fetched = get(dcm, "/credit-cases/$caseId/fmp")
        assertEquals(200, fetched.statusCode())
        assertContains(fetched.body(), "PRET-2026-001")
    }

    @Test
    fun `a DRI member can read but not generate the FMP`() {
        val driId = member("fmp-http-dri2@banque.test", Department.DRI)
        val caseId = dossierWithFinalPv(driId)
        val dri = clientFor("fmp-http-dri2@banque.test")

        val forbidden = post(dri, "/credit-cases/$caseId/fmp", """{"numeroPret":"PRET-X"}""")
        assertEquals(403, forbidden.statusCode())

        val notFound = get(dri, "/credit-cases/$caseId/fmp")
        assertEquals(404, notFound.statusCode())
    }
}
