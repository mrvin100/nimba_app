package com.nimba.review.internal

import com.nimba.analysissheet.FaSectionKey
import com.nimba.identity.Department
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

/**
 * One comment anchored to an FA section. [parentId] null makes it a thread
 * root (resolvable); replies reference their root. [pending] comments belong
 * to their author's draft [FaReview] and are invisible to everyone else until
 * the review is submitted — the GitHub draft-review behaviour.
 */
@Entity
@Table(name = "fa_review_comment")
class FaReviewComment(
    @Column(name = "credit_case_id", nullable = false, updatable = false)
    val creditCaseId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "section_key", nullable = false, updatable = false)
    val sectionKey: FaSectionKey,
    @Column(name = "review_id")
    var reviewId: UUID? = null,
    @Column(name = "parent_id", updatable = false)
    val parentId: UUID? = null,
    @Column(name = "author_id", nullable = false, updatable = false)
    val authorId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "author_department", nullable = false, updatable = false)
    val authorDepartment: Department,
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    val body: String,
    @Column(name = "pending", nullable = false)
    var pending: Boolean,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null

    @Column(name = "resolved_by")
    var resolvedBy: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
