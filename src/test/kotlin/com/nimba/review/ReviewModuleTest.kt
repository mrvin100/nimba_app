package com.nimba.review

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
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.review.internal.ReviewService
import com.nimba.seedMember
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.WorkflowModuleApi
import com.nimba.workflow.WorkflowStatus
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class ReviewModuleTest(
    @Autowired private val reviewService: ReviewService,
    @Autowired private val workflow: WorkflowModuleApi,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun memberId(department: Department): UUID =
        requireNotNull(seedMember(users, passwordEncoder, "rev-${UUID.randomUUID()}@banque.test", department).id)

    /** A dossier with a published FA, submitted to the DCM review. */
    private fun dossierEnRevueDcm(dri: UUID): UUID {
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "Client Revue"),
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
        analysisSheets.publish(caseId)
        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        return caseId
    }

    @Test
    fun `the DCM reviewer's pending comments stay invisible until the review is submitted`() {
        val dri = memberId(Department.DRI)
        val dcm = memberId(Department.DCM)
        val caseId = dossierEnRevueDcm(dri)

        val thread = reviewService.addComment(caseId, dcm, FaSectionKey.PILIER1_SYNTHESE, "Synthèse trop courte", null)
        assertTrue(thread.comments.single().pending)

        // The DRI sees nothing yet; the reviewer sees their own draft.
        assertTrue(reviewService.overview(caseId, dri).threads.isEmpty())
        assertEquals(1, reviewService.overview(caseId, dcm).myDraft?.pendingComments)

        val review = reviewService.submitReview(caseId, dcm, ReviewVerdict.CHANGEMENTS_DEMANDES, "Compléter le pilier 1")
        assertEquals(ReviewVerdict.CHANGEMENTS_DEMANDES, review.verdict)
        // The verdict fired the workflow transition and the comments became visible.
        assertEquals(WorkflowStatus.BROUILLON, workflow.statusOf(caseId))
        val driView = reviewService.overview(caseId, dri)
        assertEquals(1, driView.threads.size)
        assertFalse(
            driView.threads
                .single()
                .comments
                .single()
                .pending,
        )
    }

    @Test
    fun `the DRI replies and resolves threads while correcting`() {
        val dri = memberId(Department.DRI)
        val dcm = memberId(Department.DCM)
        val caseId = dossierEnRevueDcm(dri)
        reviewService.addComment(caseId, dcm, FaSectionKey.PILIER1_SYNTHESE, "Préciser la relation bancaire", null)
        reviewService.submitReview(caseId, dcm, ReviewVerdict.CHANGEMENTS_DEMANDES, "À corriger")
        val rootId =
            reviewService
                .overview(caseId, dri)
                .threads
                .single()
                .id

        // The DRI's reply is visible immediately (no draft review outside a review stage).
        val thread = reviewService.addComment(caseId, dri, FaSectionKey.PILIER1_SYNTHESE, "Corrigé dans la synthèse", rootId)
        assertEquals(2, thread.comments.size)
        assertFalse(thread.comments.last().pending)

        val resolved = reviewService.resolve(caseId, dri, rootId, resolved = true)
        assertNotNull(resolved.resolvedAt)
        assertEquals(0, reviewService.overview(caseId, dri).unresolvedCount)
    }

    @Test
    fun `the DRC's observations send the dossier to the corrections lane`() {
        val dri = memberId(Department.DRI)
        val dcm = memberId(Department.DCM)
        val drc = memberId(Department.DRC)
        val caseId = dossierEnRevueDcm(dri)
        reviewService.submitReview(caseId, dcm, ReviewVerdict.APPROUVE, null)

        reviewService.addComment(caseId, drc, FaSectionKey.PILIER4_RISQUES, "Risque de concentration à couvrir", null)
        reviewService.submitReview(caseId, drc, ReviewVerdict.OBSERVATIONS, "Voir observations sur les risques")

        assertEquals(WorkflowStatus.CORRECTIONS_DRI, workflow.statusOf(caseId))
        assertEquals(1, reviewService.overview(caseId, dri).unresolvedCount)
    }

    @Test
    fun `a verdict outside the caller's direction or turn is rejected`() {
        val dri = memberId(Department.DRI)
        val dcm = memberId(Department.DCM)
        val caseId = dossierEnRevueDcm(dri)

        // A DCM reviewer cannot use a DRC verdict.
        assertFailsWith<ResponseStatusException> {
            reviewService.submitReview(caseId, dcm, ReviewVerdict.AVIS_FAVORABLE, null)
        }
        // The DRI is not the active reviewer.
        assertFailsWith<ResponseStatusException> {
            reviewService.submitReview(caseId, dri, ReviewVerdict.APPROUVE, null)
        }
    }

    @Test
    fun `a comment on a section outside the dossier's variant is rejected`() {
        val dri = memberId(Department.DRI)
        val dcm = memberId(Department.DCM)
        val caseId = dossierEnRevueDcm(dri)

        // AVEC_CONTRAT has no payer synthesis (SANS_CONTRAT only).
        assertFailsWith<ResponseStatusException> {
            reviewService.addComment(caseId, dcm, FaSectionKey.PILIER1_SYNTHESE_PAYEUR, "Hors variante", null)
        }
    }

    @Test
    fun `only the author deletes their own pending comment`() {
        val dri = memberId(Department.DRI)
        val dcm = memberId(Department.DCM)
        val caseId = dossierEnRevueDcm(dri)
        val thread = reviewService.addComment(caseId, dcm, FaSectionKey.PILIER1_SYNTHESE, "Brouillon", null)
        val commentId = thread.comments.single().id

        assertFailsWith<ResponseStatusException> { reviewService.deletePendingComment(caseId, dri, commentId) }
        reviewService.deletePendingComment(caseId, dcm, commentId)
        assertNull(reviewService.overview(caseId, dcm).threads.firstOrNull())
    }
}
