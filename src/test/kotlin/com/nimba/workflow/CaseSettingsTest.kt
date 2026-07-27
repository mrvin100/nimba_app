package com.nimba.workflow

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.creditcase.ResettableDocument
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.seedMember
import com.nimba.workflow.internal.CaseSettingsController
import com.nimba.workflow.internal.WorkflowService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CaseSettingsTest(
    @Autowired private val settings: CaseSettingsController,
    @Autowired private val workflow: WorkflowService,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val scheduleApi: AmortizationScheduleModuleApi,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun preparedCase(): Pair<UUID, UUID> {
        val dri = requireNotNull(seedMember(users, passwordEncoder, "set-${UUID.randomUUID()}@banque.test", Department.DRI).id)
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "Client Settings"),
                        ProductType.LEASING,
                        "GNF",
                        dri,
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
        schedules.saveAndFlush(AmortizationSchedule(caseId, 1, "echeancier.csv", dri).apply { addLine(line) })
        analysisSheets.create(CreateAnalysisSheetCommand(caseId, dri))
        return caseId to dri
    }

    @Test
    @WithMockUser(roles = ["DRI_MANAGER"])
    fun `a DRI manager resets one document of a draft dossier`() {
        val (caseId, _) = preparedCase()
        assertNotNull(analysisSheets.findByCase(caseId))

        settings.reset(caseId, ResettableDocument.FICHE_ANALYSE)
        assertNull(analysisSheets.findByCase(caseId), "the FA must be wiped")
        // The TA is untouched by an FA reset.
        assertNotNull(scheduleApi.scheduleSummary(caseId))

        settings.reset(caseId, ResettableDocument.AMORTISSEMENT)
        assertFalse(scheduleApi.hasScheduleForCase(caseId), "the TA must be wiped")
    }

    @Test
    @WithMockUser(roles = ["DRI_MANAGER"])
    fun `critical actions are refused once the dossier left BROUILLON`() {
        val (caseId, dri) = preparedCase()
        analysisSheets.publish(caseId)
        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)

        val error =
            assertFailsWith<ResponseStatusException> {
                settings.reset(caseId, ResettableDocument.FICHE_ANALYSE)
            }
        assertEquals(409, error.statusCode.value())
        assertNotNull(analysisSheets.findByCase(caseId))
    }
}
