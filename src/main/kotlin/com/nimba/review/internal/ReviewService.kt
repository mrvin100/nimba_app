package com.nimba.review.internal

import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.FaSectionKey
import com.nimba.analysissheet.FaSectionRegistry
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.identity.Department
import com.nimba.identity.IdentityModuleApi
import com.nimba.review.ReviewVerdict
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.WorkflowModuleApi
import com.nimba.workflow.WorkflowStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * GitHub-style FA reviews (design §12.2). The active reviewer (DCM while
 * EN_REVUE_DCM, DRC while EN_REVUE_DRC) batches per-section comments into a
 * draft review — pending, invisible to everyone else — and submits them with a
 * verdict that fires the matching workflow transition atomically. Everyone
 * else's comments (a DRI reply while correcting, a DCM note during
 * verification) are visible immediately. Thread roots are resolved by the DRI
 * as corrections land, or by their author.
 */
@Service
class ReviewService(
    private val reviews: FaReviewRepository,
    private val comments: FaReviewCommentRepository,
    private val creditCases: CreditCaseModuleApi,
    private val analysisSheets: AnalysisSheetModuleApi,
    private val workflow: WorkflowModuleApi,
    private val identity: IdentityModuleApi,
) {
    @Transactional(readOnly = true)
    fun overview(
        creditCaseId: UUID,
        callerId: UUID,
    ): ReviewOverviewResponse {
        creditCases.getOrThrow(creditCaseId)
        val visible =
            comments
                .findByCreditCaseIdOrderByCreatedAtAsc(creditCaseId)
                .filter { !it.pending || it.authorId == callerId }
        val byRoot = visible.filter { it.parentId != null }.groupBy { it.parentId }
        val threads =
            visible
                .filter { it.parentId == null }
                .map { root -> toThread(root, byRoot[root.id].orEmpty()) }
        val submitted =
            reviews
                .findByCreditCaseIdAndStatusOrderBySubmittedAtAsc(creditCaseId, ReviewStatus.SOUMISE)
                .map { it.toResponse(identity.findUser(it.reviewerId)?.fullName ?: "—") }
        val draft = reviews.findByCreditCaseIdAndReviewerIdAndStatus(creditCaseId, callerId, ReviewStatus.EN_COURS)
        return ReviewOverviewResponse(
            threads = threads,
            reviews = submitted,
            myDraft =
                draft?.let { review ->
                    DraftReviewResponse(
                        id = requireNotNull(review.id),
                        pendingComments = comments.findByReviewId(requireNotNull(review.id)).count { it.pending },
                    )
                },
            unresolvedCount = threads.count { it.resolvedAt == null },
        )
    }

    @Transactional
    fun addComment(
        creditCaseId: UUID,
        callerId: UUID,
        sectionKey: FaSectionKey,
        body: String,
        parentId: UUID?,
    ): ReviewThreadResponse {
        creditCases.getOrThrow(creditCaseId)
        if (body.isBlank()) throw badRequest("Le commentaire ne peut pas être vide")
        requireSectionOfVariant(creditCaseId, sectionKey)
        val departments = identity.departmentsOf(callerId)
        if (departments.none { it in COMMENTING_DEPARTMENTS }) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Seules les directions DRI, DCM et DRC commentent la fiche d'analyse")
        }
        val root =
            parentId?.let { id ->
                val found = comments.findById(id).orElse(null)
                if (found == null || found.creditCaseId != creditCaseId || found.parentId != null) {
                    throw ResponseStatusException(HttpStatus.NOT_FOUND, "Fil de discussion introuvable")
                }
                if (found.pending) throw badRequest("Impossible de répondre à un commentaire non encore soumis")
                found
            }

        // The active reviewer's comments are batched in their draft review
        // (created on first use), pending until the review is submitted.
        val reviewingAs = activeReviewerDepartment(creditCaseId, departments)
        val comment =
            if (reviewingAs != null) {
                val draft = draftReviewFor(creditCaseId, callerId, reviewingAs)
                FaReviewComment(
                    creditCaseId = creditCaseId,
                    sectionKey = root?.sectionKey ?: sectionKey,
                    reviewId = draft.id,
                    parentId = root?.id,
                    authorId = callerId,
                    authorDepartment = reviewingAs,
                    body = body,
                    pending = true,
                )
            } else {
                FaReviewComment(
                    creditCaseId = creditCaseId,
                    sectionKey = root?.sectionKey ?: sectionKey,
                    parentId = root?.id,
                    authorId = callerId,
                    authorDepartment = departments.first { it in COMMENTING_DEPARTMENTS },
                    body = body,
                    pending = false,
                )
            }
        comments.save(comment)
        val threadRoot = root ?: comment
        return toThread(
            threadRoot,
            comments
                .findByCreditCaseIdOrderByCreatedAtAsc(creditCaseId)
                .filter { it.parentId == threadRoot.id && (!it.pending || it.authorId == callerId) },
        )
    }

    @Transactional
    fun deletePendingComment(
        creditCaseId: UUID,
        callerId: UUID,
        commentId: UUID,
    ) {
        val comment = requireComment(creditCaseId, commentId)
        if (!comment.pending || comment.authorId != callerId) {
            throw badRequest("Seul un commentaire en attente peut être supprimé, par son auteur")
        }
        comments.delete(comment)
    }

    @Transactional
    fun resolve(
        creditCaseId: UUID,
        callerId: UUID,
        commentId: UUID,
        resolved: Boolean,
    ): ReviewThreadResponse {
        val root = requireComment(creditCaseId, commentId)
        if (root.parentId != null || root.pending) throw badRequest("Seule la racine d'un fil soumis peut être résolue")
        val departments = identity.departmentsOf(callerId)
        if (Department.DRI !in departments && root.authorId != callerId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le DRI ou l'auteur du fil peut le résoudre")
        }
        root.resolvedAt = if (resolved) Instant.now() else null
        root.resolvedBy = if (resolved) callerId else null
        return toThread(root, comments.findByCreditCaseIdOrderByCreatedAtAsc(creditCaseId).filter { it.parentId == root.id })
    }

    @Transactional
    fun submitReview(
        creditCaseId: UUID,
        callerId: UUID,
        verdict: ReviewVerdict,
        summary: String?,
    ): ReviewResponse {
        val departments = identity.departmentsOf(callerId)
        val reviewingAs =
            activeReviewerDepartment(creditCaseId, departments)
                ?: throw conflict("Le dossier n'est pas à votre revue actuellement")
        if (verdict.department != reviewingAs) {
            throw badRequest("Ce verdict n'appartient pas à votre direction")
        }
        val review = draftReviewFor(creditCaseId, callerId, reviewingAs)
        review.status = ReviewStatus.SOUMISE
        review.verdict = verdict
        review.summary = summary?.takeIf { it.isNotBlank() }
        review.submittedAt = Instant.now()
        comments.findByReviewId(requireNotNull(review.id)).forEach { it.pending = false }

        // The verdict IS the workflow move — same gates, one transaction. The
        // workflow requires a comment on request-changes actions, so an empty
        // summary is rejected there with the proper message.
        workflow.act(creditCaseId, callerId, verdict.workflowAction, review.summary)
        return review.toResponse(identity.findUser(callerId)?.fullName ?: "—")
    }

    private fun draftReviewFor(
        creditCaseId: UUID,
        callerId: UUID,
        department: Department,
    ): FaReview =
        reviews.findByCreditCaseIdAndReviewerIdAndStatus(creditCaseId, callerId, ReviewStatus.EN_COURS)
            ?: reviews.save(FaReview(creditCaseId, callerId, department))

    /** The caller's department if the dossier is currently at their review stage, else null. */
    private fun activeReviewerDepartment(
        creditCaseId: UUID,
        departments: Set<Department>,
    ): Department? =
        when (workflow.statusOf(creditCaseId)) {
            WorkflowStatus.EN_REVUE_DCM -> Department.DCM.takeIf { it in departments }
            WorkflowStatus.EN_REVUE_DRC -> Department.DRC.takeIf { it in departments }
            else -> null
        }

    private fun requireSectionOfVariant(
        creditCaseId: UUID,
        sectionKey: FaSectionKey,
    ) {
        val variant =
            analysisSheets.findByCase(creditCaseId)?.faVariant
                ?: throw conflict("Aucune fiche d'analyse à commenter pour ce dossier")
        if (sectionKey !in FaSectionRegistry.sectionsFor(variant)) {
            throw badRequest("La section « ${sectionKey.label} » ne s'applique pas à ce dossier")
        }
    }

    private fun requireComment(
        creditCaseId: UUID,
        commentId: UUID,
    ): FaReviewComment {
        val comment = comments.findById(commentId).orElse(null)
        if (comment == null || comment.creditCaseId != creditCaseId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Commentaire introuvable")
        }
        return comment
    }

    private fun toThread(
        root: FaReviewComment,
        replies: List<FaReviewComment>,
    ): ReviewThreadResponse =
        ReviewThreadResponse(
            id = requireNotNull(root.id),
            sectionKey = root.sectionKey,
            resolvedAt = root.resolvedAt,
            resolvedBy = root.resolvedBy,
            comments = (listOf(root) + replies.sortedBy { it.createdAt }).map { it.toResponse() },
        )

    private fun FaReviewComment.toResponse(): ReviewCommentResponse =
        ReviewCommentResponse(
            id = requireNotNull(id),
            sectionKey = sectionKey,
            parentId = parentId,
            authorId = authorId,
            authorName = identity.findUser(authorId)?.fullName ?: "—",
            authorDepartment = authorDepartment,
            body = body,
            pending = pending,
            createdAt = createdAt,
        )

    private fun FaReview.toResponse(reviewerName: String): ReviewResponse =
        ReviewResponse(
            id = requireNotNull(id),
            reviewerId = reviewerId,
            reviewerName = reviewerName,
            department = department,
            verdict = requireNotNull(verdict),
            summary = summary,
            submittedAt = requireNotNull(submittedAt),
        )

    private fun conflict(message: String) = ResponseStatusException(HttpStatus.CONFLICT, message)

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private val ReviewVerdict.department: Department
        get() =
            when (this) {
                ReviewVerdict.APPROUVE, ReviewVerdict.CHANGEMENTS_DEMANDES -> Department.DCM
                ReviewVerdict.AVIS_FAVORABLE, ReviewVerdict.OBSERVATIONS -> Department.DRC
            }

    private val ReviewVerdict.workflowAction: WorkflowAction
        get() =
            when (this) {
                ReviewVerdict.APPROUVE, ReviewVerdict.AVIS_FAVORABLE -> WorkflowAction.APPROVE
                ReviewVerdict.CHANGEMENTS_DEMANDES, ReviewVerdict.OBSERVATIONS -> WorkflowAction.REQUEST_CHANGES
            }

    private companion object {
        val COMMENTING_DEPARTMENTS = setOf(Department.DRI, Department.DCM, Department.DRC)
    }
}
