package com.nimba.review.internal

import com.nimba.review.ReviewVerdict
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FaReviewRepository : JpaRepository<FaReview, UUID> {
    fun findByCreditCaseIdAndReviewerIdAndStatus(
        creditCaseId: UUID,
        reviewerId: UUID,
        status: ReviewStatus,
    ): FaReview?

    fun findByCreditCaseIdAndStatusOrderBySubmittedAtAsc(
        creditCaseId: UUID,
        status: ReviewStatus,
    ): List<FaReview>

    fun findByCreditCaseIdAndVerdictInOrderBySubmittedAtDesc(
        creditCaseId: UUID,
        verdicts: Collection<ReviewVerdict>,
    ): List<FaReview>

    fun deleteByCreditCaseId(creditCaseId: UUID)
}

interface FaReviewCommentRepository : JpaRepository<FaReviewComment, UUID> {
    fun findByCreditCaseIdOrderByCreatedAtAsc(creditCaseId: UUID): List<FaReviewComment>

    fun findByReviewId(reviewId: UUID): List<FaReviewComment>

    fun deleteByCreditCaseId(creditCaseId: UUID)
}
