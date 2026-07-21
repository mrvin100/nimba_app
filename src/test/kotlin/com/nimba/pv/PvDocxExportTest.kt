package com.nimba.pv

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.analysissheet.FaSectionKey
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.creditcase.UpdateClientIdentityCommand
import com.nimba.creditcase.UpdateConditionsDeBanqueCommand
import com.nimba.guarantee.CreateGuaranteeCommand
import com.nimba.guarantee.GuaranteeKind
import com.nimba.guarantee.GuaranteeModuleApi
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.pv.internal.PvDocxExportService
import com.nimba.seedMember
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.internal.WorkflowService
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class PvDocxExportTest(
    @Autowired private val pvs: PvModuleApi,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val guarantees: GuaranteeModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val workflow: WorkflowService,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val export: PvDocxExportService,
) {
    private fun memberId(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

    private fun approvedDossier(): UUID {
        val dri = memberId("pv-export-dri-${UUID.randomUUID()}@banque.test", Department.DRI)
        val dcm = memberId("pv-export-dcm-${UUID.randomUUID()}@banque.test", Department.DCM)
        val drc = memberId("pv-export-drc-${UUID.randomUUID()}@banque.test", Department.DRC)
        val comite1 = memberId("pv-export-comite1-${UUID.randomUUID()}@banque.test", Department.COMITE)
        val comite2 = memberId("pv-export-comite2-${UUID.randomUUID()}@banque.test", Department.COMITE)

        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand("ICAB CONSTRUCTION", ProductType.LEASING, "GNF", dri, contractType = ContractType.AVEC_CONTRAT),
                ).id
        val line =
            AmortizationScheduleLine(
                numeroEcheance = "1",
                dateEcheance = LocalDate.of(2026, 5, 1),
                interet = BigDecimal("100.0000"),
                equipement = BigDecimal("4818561620.0000"),
                assurance = BigDecimal("224463196.0000"),
                tracking = BigDecimal.ZERO,
                immatriculation = BigDecimal("40000000.0000"),
                capital = BigDecimal("900.0000"),
                loyerHt = BigDecimal("1000.0000"),
                taxes = BigDecimal.ZERO,
                loyerTtc = BigDecimal("1000.0000"),
                capitalRestantDu = BigDecimal.ZERO,
            )
        schedules.saveAndFlush(AmortizationSchedule(caseId, 1, "echeancier.csv", dri).apply { addLine(line) })
        analysisSheets.create(CreateAnalysisSheetCommand(caseId, dri))
        creditCases.updateIdentity(
            caseId,
            UpdateClientIdentityCommand(formeJuridique = "SARL", principalDirigeant = "CAMARA ISMAEL", agence = "MADINA"),
        )
        creditCases.updateConditionsDeBanque(
            caseId,
            UpdateConditionsDeBanqueCommand(fraisMiseEnPlacePct = BigDecimal("1"), valeurResiduellePct = BigDecimal("2")),
        )
        guarantees.create(CreateGuaranteeCommand(caseId, GuaranteeKind.DETENUE, "4 camions sous leasing", dri))
        guarantees.create(CreateGuaranteeCommand(caseId, GuaranteeKind.A_RECUEILLIR, "Caution personnelle et solidaire", dri))
        analysisSheets.updateSection(caseId, FaSectionKey.CONCLUSION_POINTS_FORTS, "Bonne expérience du client")
        analysisSheets.updateSection(caseId, FaSectionKey.CONCLUSION_POINTS_FAIBLES, "Repose sur son promoteur")
        analysisSheets.publish(caseId)

        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)
        workflow.act(caseId, drc, WorkflowAction.APPROVE, null)
        workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null)
        workflow.act(caseId, comite1, WorkflowAction.APPROVE, null)
        workflow.act(caseId, comite2, WorkflowAction.APPROVE, null)
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
    fun `exports the finalized PV as the replica structure`() {
        val caseId = approvedDossier()
        val dcm = memberId("pv-export-draft-dcm@banque.test", Department.DCM)
        pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 3, 18)))
        pvs.updateDraft(
            caseId,
            UpdatePvDraftCommand(
                seanceDate = LocalDate.of(2026, 3, 18),
                rapporteur = "DETCHENOU QUENTIN",
                president = "GUY LAURENT FONDJO",
                debats = listOf(PvDebat("Situation actuelle des engagements", "Client vient de rembourser", "Ras")),
            ),
        )
        pvs.finalize(caseId)

        val result = export.export(caseId)
        val text = allText(result.content)

        assertTrue(result.filename.startsWith("pv-"))
        assertContains(text, "PROCES VERBAL DE COMITE DE CREDIT")
        assertContains(text, "SEANCE DU 18/03/2026")
        assertContains(text, "I. ICAB CONSTRUCTION")
        assertContains(text, "SARL")
        assertContains(text, "CAMARA ISMAEL")
        assertContains(text, "BESOIN EXPRIME")
        assertContains(text, "4 818 561 620")
        assertContains(text, "DEBATS DU COMITE")
        assertContains(text, "Situation actuelle des engagements")
        assertContains(text, "POINTS FORTS")
        assertContains(text, "Bonne expérience du client")
        assertContains(text, "POINTS FAIBLES")
        assertContains(text, "DECISION DU COMITE")
        assertContains(text, "GARANTIES")
        assertContains(text, "4 camions sous leasing")
        assertContains(text, "Caution personnelle et solidaire")
        assertContains(text, "CONDITIONS DE BANQUE")
        assertContains(text, "DETCHENOU QUENTIN")
        assertContains(text, "GUY LAURENT FONDJO")

        val doc = XWPFDocument(ByteArrayInputStream(result.content))
        val fonts =
            doc.paragraphs
                .flatMap { it.runs }
                .mapNotNull { it.fontFamily }
                .toSet()
        assertEquals(setOf("Tahoma"), fonts)
        doc.close()
    }

    @Test
    fun `a draft PV cannot be exported`() {
        val caseId = approvedDossier()
        val dcm = memberId("pv-export-nofinal-dcm@banque.test", Department.DCM)
        pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 3, 18)))

        assertFailsWith<ResponseStatusException> { export.export(caseId) }
    }
}
