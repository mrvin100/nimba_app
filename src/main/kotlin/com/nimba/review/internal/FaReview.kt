package com.nimba.review.internal

import com.nimba.identity.Department
import com.nimba.review.ReviewVerdict
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A review's lifecycle: a draft holding pending comments, then submitted with its verdict. */
enum class ReviewStatus { EN_COURS, SOUMISE }

/**
 * One reviewer pass over a dossier's FA — the GitHub "review": created on the
 * first pending comment (or at submit time), it batches the reviewer's
 * comments until they submit a [verdict] with an optional [summary]. One
 * EN_COURS review at most per (case, reviewer).
 */
@Entity
@Table(name = "fa_review")
class FaReview(
    @Column(name = "credit_case_id", nullable = false, updatable = false)
    val creditCaseId: UUID,
    @Column(name = "reviewer_id", nullable = false, updatable = false)
    val reviewerId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, updatable = false)
    val department: Department,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: ReviewStatus = ReviewStatus.EN_COURS

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict")
    var verdict: ReviewVerdict? = null

    @Column(name = "summary", columnDefinition = "TEXT")
    var summary: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "submitted_at")
    var submittedAt: Instant? = null
}
