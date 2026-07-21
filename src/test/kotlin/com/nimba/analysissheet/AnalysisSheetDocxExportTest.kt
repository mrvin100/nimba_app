package com.nimba.analysissheet

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.analysissheet.internal.AnalysisSheetDocxExportService
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.Department
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.WorkflowStatus
import com.nimba.workflow.internal.WorkflowEvent
import com.nimba.workflow.internal.WorkflowEventRepository
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AnalysisSheetDocxExportTest(
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val sheets: AnalysisSheetModuleApi,
    @Autowired private val export: AnalysisSheetDocxExportService,
    @Autowired private val workflowEvents: WorkflowEventRepository,
    @Autowired private val users: UserRepository,
) {
    private fun analystId(): UUID =
        requireNotNull(
            users
                .saveAndFlush(
                    User(fullName = "Analyste FA", email = "fa-${UUID.randomUUID()}@banque.test", passwordHash = "hash"),
                ).id,
        )

    private fun preparedCaseId(analyst: UUID): UUID {
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand("SOCIETE TEST", ProductType.LEASING, "GNF", analyst, contractType = ContractType.AVEC_CONTRAT),
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
        schedules.saveAndFlush(
            AmortizationSchedule(
                creditCaseId = caseId,
                versionNumber = 1,
                originalFilename = "echeancier.csv",
                uploadedBy = UUID.randomUUID(),
            ).apply { addLine(line) },
        )
        return caseId
    }

    private fun allText(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            buildString {
                doc.paragraphs.forEach { appendLine(it.text) }
                doc.tables.forEach { table ->
                    table.rows.forEach { row -> row.tableCells.forEach { appendLine(it.text) } }
                }
            }
        }

    @Test
    fun `exports the replica structure with cover, piliers, conclusion and the écheancier`() {
        val analyst = analystId()
        val caseId = preparedCaseId(analyst)
        sheets.create(CreateAnalysisSheetCommand(caseId, analyst))
        sheets.updateSection(caseId, FaSectionKey.PILIER1_SYNTHESE, "Relation jugée favorable.")
        sheets.updateSection(
            caseId,
            FaSectionKey.PILIER1_SIGNATAIRES,
            """{"rows":[{"nom":"DIALLO A.","piece":"R0423513","validite":"16/10/2026"}]}""",
        )
        sheets.updateSection(
            caseId,
            FaSectionKey.PILIER2_CONTRAT,
            """{"maitreOuvrage":"TOTAL ENERGIES","domiciliation":"AFRILAND FIRST BANK"}""",
        )

        val result = export.export(caseId)
        val text = allText(result.content)

        assertTrue(result.filename.startsWith("fiche-analyse-"))
        // Cover + avis blocks.
        assertContains(text, "INFORMATIONS SUR LA DEMANDE")
        assertContains(text, "INFORMATIONS INTERNES")
        assertContains(text, "PROPOSITIONS DE DECISION DE L'ANALYSTE")
        assertContains(text, "AVIS DE LA DIRECTION RECHERCHE ET INVESTISSEMENT")
        // Piliers in document order with the real headings.
        assertContains(text, "PILIER 1 : CONNAISSANCE DE L'ENTREPRISE")
        assertContains(text, "PILIER 2 : ANALYSE DU MARCHE")
        assertContains(text, "PILIER 3 : ANALYSE FINANCIERE")
        assertContains(text, "4. SYNTHESE DES RISQUES ET PRESENTATION DES SURETES")
        assertContains(text, "V – CONCLUSION")
        // Saved section content lands in the document.
        assertContains(text, "Relation jugée favorable.")
        assertContains(text, "R0423513")
        assertContains(text, "AFRILAND FIRST BANK")
        // §4.1 default risk matrix prints even though never edited.
        assertContains(text, "Risque de crédit")
        // §3.5 reprints the imported échéancier rows.
        assertContains(text, "01/05/2026")
        // Typography follows the template.
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
    fun `prints the comité's completion observations from the workflow loop`() {
        val analyst = analystId()
        val caseId = preparedCaseId(analyst)
        workflowEvents.saveAndFlush(
            WorkflowEvent(
                creditCaseId = caseId,
                actorId = analyst,
                actorDepartment = Department.COMITE,
                action = WorkflowAction.REQUEST_COMPLETION,
                fromStatus = WorkflowStatus.PRET_POUR_COMITE,
                toStatus = WorkflowStatus.BROUILLON,
                comment = "Titre foncier\nNon endettement",
            ),
        )

        val text = allText(export.export(caseId).content)

        assertContains(text, "LES OBSERVATIONS SUR LE DOSSIER LORS DU DERNIER COMITE DE CREDIT")
        assertContains(text, "Titre foncier")
        assertContains(text, "Encours")
    }

    @Test
    fun `exports the full skeleton with RAS even when the FA was never initiated`() {
        val analyst = analystId()
        val caseId = preparedCaseId(analyst)

        val text = allText(export.export(caseId).content)

        assertContains(text, "PILIER 1 : CONNAISSANCE DE L'ENTREPRISE")
        assertContains(text, "1.16 SYNTHÈSE SUR LA CONNAISSANCE DE LA RELATION")
        assertContains(text, "RAS")
    }
}
