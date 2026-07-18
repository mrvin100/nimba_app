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
import com.nimba.creditcase.UpdateConditionsDeBanqueCommand
import com.nimba.guarantee.CreateGuaranteeCommand
import com.nimba.guarantee.GuaranteeKind
import com.nimba.guarantee.GuaranteeModuleApi
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.pv.CreatePvCommand
import com.nimba.pv.PvModuleApi
import com.nimba.seedMember
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.internal.WorkflowService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class FmpModuleTest(
    @Autowired private val fmps: FmpModuleApi,
    @Autowired private val pvs: PvModuleApi,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val guarantees: GuaranteeModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val workflow: WorkflowService,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun memberId(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

    /** A dossier with a finalized PV, ready for FMP generation. */
    private fun dossierWithFinalPv(): UUID {
        val dri = memberId("fmp-dri-${UUID.randomUUID()}@banque.test", Department.DRI)
        val dcm = memberId("fmp-dcm-${UUID.randomUUID()}@banque.test", Department.DCM)
        val drc = memberId("fmp-drc-${UUID.randomUUID()}@banque.test", Department.DRC)
        val comite1 = memberId("fmp-comite1-${UUID.randomUUID()}@banque.test", Department.COMITE)
        val comite2 = memberId("fmp-comite2-${UUID.randomUUID()}@banque.test", Department.COMITE)

        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand("Client FMP", ProductType.LEASING, "GNF", dri, contractType = ContractType.AVEC_CONTRAT),
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
        analysisSheets.publish(caseId)
        creditCases.updateConditionsDeBanque(caseId, UpdateConditionsDeBanqueCommand(tauxInteretPct = BigDecimal("9.5")))
        guarantees.create(CreateGuaranteeCommand(caseId, GuaranteeKind.DETENUE, "Nantissement matériel", dri))

        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
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
    fun `generates the FMP as an extract of the finalized PV`() {
        val caseId = dossierWithFinalPv()
        val dcm = memberId("fmp-gen-dcm@banque.test", Department.DCM)

        val fmp = fmps.create(CreateFmpCommand(caseId, dcm, "PRET-2026-001", "GAR-001"))

        assertEquals("PRET-2026-001", fmp.numeroPret)
        assertEquals("GAR-001", fmp.garantieRef)
        assertEquals(1, fmp.garanties.size)
        assertEquals("Nantissement matériel", fmp.garanties.first().description)
        assertEquals(BigDecimal("9.500"), fmp.conditionsDeBanque.tauxInteretPct)
        assertEquals(BigDecimal("900.0000"), fmp.articulation.loanAmount)

        val reloaded = requireNotNull(fmps.findByCase(caseId))
        assertEquals("PRET-2026-001", reloaded.numeroPret)
    }

    @Test
    fun `cannot generate an FMP before the PV is finalized`() {
        val dri = memberId("fmp-early-dri@banque.test", Department.DRI)
        val dcm = memberId("fmp-early-dcm@banque.test", Department.DCM)
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand("Trop tôt", ProductType.LEASING, "GNF", dri, contractType = ContractType.AVEC_CONTRAT),
                ).id

        assertFailsWith<ResponseStatusException> {
            fmps.create(CreateFmpCommand(caseId, dcm, "PRET-X", null))
        }
    }

    @Test
    fun `a second FMP cannot be generated for the same case`() {
        val caseId = dossierWithFinalPv()
        val dcm = memberId("fmp-dup-dcm@banque.test", Department.DCM)
        fmps.create(CreateFmpCommand(caseId, dcm, "PRET-1", null))

        assertFailsWith<ResponseStatusException> {
            fmps.create(CreateFmpCommand(caseId, dcm, "PRET-2", null))
        }
    }

    @Test
    fun `deleting the case purges its FMP`() {
        val caseId = dossierWithFinalPv()
        val dcm = memberId("fmp-purge-dcm@banque.test", Department.DCM)
        fmps.create(CreateFmpCommand(caseId, dcm, "PRET-1", null))

        creditCases.delete(caseId)

        assertNull(fmps.findByCase(caseId))
    }
}
