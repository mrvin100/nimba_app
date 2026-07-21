package com.nimba.review.internal

import com.nimba.analysissheet.FaSectionKey
import com.nimba.identity.Department
import com.nimba.review.ReviewVerdict
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class ReviewCommentRequest(
    val sectionKey: FaSectionKey,
    @field:NotBlank(message = "Le commentaire ne peut pas être vide")
    @field:Size(max = 5000, message = "5 000 caractères maximum")
    val body: String,
    val parentId: UUID? = null,
)

data class ReviewSubmitRequest(
    val verdict: ReviewVerdict,
    @field:Size(max = 5000, message = "5 000 caractères maximum")
    val summary: String? = null,
)

data class ReviewCommentResponse(
    val id: UUID,
    val sectionKey: FaSectionKey,
    val parentId: UUID?,
    val authorId: UUID,
    val authorName: String,
    val authorDepartment: Department,
    val body: String,
    /** True only for the caller's own not-yet-submitted comments. */
    val pending: Boolean,
    val createdAt: Instant,
)

data class ReviewThreadResponse(
    val id: UUID,
    val sectionKey: FaSectionKey,
    val resolvedAt: Instant?,
    val resolvedBy: UUID?,
    /** Root first, then replies in chronological order. */
    val comments: List<ReviewCommentResponse>,
)

data class ReviewResponse(
    val id: UUID,
    val reviewerId: UUID,
    val reviewerName: String,
    val department: Department,
    val verdict: ReviewVerdict,
    val summary: String?,
    val submittedAt: Instant,
)

data class DraftReviewResponse(
    val id: UUID,
    val pendingComments: Int,
)

data class ReviewOverviewResponse(
    val threads: List<ReviewThreadResponse>,
    val reviews: List<ReviewResponse>,
    val myDraft: DraftReviewResponse?,
    val unresolvedCount: Int,
)
