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
import com.nimba.fmp.internal.FmpDocxExportService
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
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class FmpDocxExportTest(
    @Autowired private val fmps: FmpModuleApi,
    @Autowired private val pvs: PvModuleApi,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val guarantees: GuaranteeModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val workflow: WorkflowService,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val export: FmpDocxExportService,
) {
    private fun memberId(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

    private fun dossierWithFinalPv(): UUID {
        val dri = memberId("fmp-export-dri-${UUID.randomUUID()}@banque.test", Department.DRI)
        val dcm = memberId("fmp-export-dcm-${UUID.randomUUID()}@banque.test", Department.DCM)
        val drc = memberId("fmp-export-drc-${UUID.randomUUID()}@banque.test", Department.DRC)
        val comite1 = memberId("fmp-export-comite1-${UUID.randomUUID()}@banque.test", Department.COMITE)
        val comite2 = memberId("fmp-export-comite2-${UUID.randomUUID()}@banque.test", Department.COMITE)

        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        "OUSMANE CAMARA ET FRERES",
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
                equipement = BigDecimal("2465552614.0000"),
                assurance = BigDecimal.ZERO,
                tracking = BigDecimal.ZERO,
                immatriculation = BigDecimal.ZERO,
                capital = BigDecimal("2465552614.0000"),
                loyerHt = BigDecimal("1000.0000"),
                taxes = BigDecimal.ZERO,
                loyerTtc = BigDecimal("1000.0000"),
                capitalRestantDu = BigDecimal.ZERO,
            )
        schedules.saveAndFlush(AmortizationSchedule(caseId, 1, "echeancier.csv", dri).apply { addLine(line) })
        analysisSheets.create(CreateAnalysisSheetCommand(caseId, dri))
        analysisSheets.publish(caseId)
        creditCases.updateConditionsDeBanque(caseId, UpdateConditionsDeBanqueCommand(tauxInteretPct = BigDecimal("14")))
        guarantees.create(
            CreateGuaranteeCommand(caseId, GuaranteeKind.A_RECUEILLIR, "Lettre de domiciliation irrévocable", dri),
        )

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

    private fun allText(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            buildString {
                doc.paragraphs.forEach { appendLine(it.text) }
                doc.tables.forEach { table -> table.rows.forEach { row -> row.tableCells.forEach { appendLine(it.text) } } }
            }
        }

    @Test
    fun `exports the FMP as the replica structure`() {
        val caseId = dossierWithFinalPv()
        val dcm = memberId("fmp-export-gen-dcm@banque.test", Department.DCM)
        fmps.create(CreateFmpCommand(caseId, dcm, "PRET-2026-042", "GAR-042"))

        val result = export.export(caseId)
        val text = allText(result.content)

        assertTrue(result.filename.startsWith("fmp-"))
        assertContains(text, "FICHE DE MISE EN PLACE EN LOYER")
        assertContains(text, "PRET-2026-042")
        assertContains(text, "GAR-042")
        assertContains(text, "OUSMANE CAMARA ET FRERES")
        assertContains(text, "DECISION DU COMITE")
        assertContains(text, "2 465 552 614")
        assertContains(text, "ARTICULATION DES FINANCEMENTS")
        assertContains(text, "Leasing")
        assertContains(text, "GARANTIES")
        assertContains(text, "Lettre de domiciliation irrévocable")
        assertContains(text, "CONDITION DE BANQUE")
        assertContains(text, "14")
        assertContains(text, "DRI")
        assertContains(text, "EXCO")

        val doc = XWPFDocument(ByteArrayInputStream(result.content))
        val fonts =
            doc.paragraphs
                .flatMap { it.runs }
                .mapNotNull { it.fontFamily }
                .toSet()
        assertEquals(setOf("Tahoma"), fonts)
        doc.close()
    }
}
