package com.nimba.review.internal

import com.nimba.creditcase.CreditCaseDeleted
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Purges a case's reviews and comment threads when an administrator deletes the case. */
@Component
class ReviewPurgeListener(
    private val reviews: FaReviewRepository,
    private val comments: FaReviewCommentRepository,
) {
    @EventListener
    fun purge(event: CreditCaseDeleted) {
        comments.deleteByCreditCaseId(event.creditCaseId)
        reviews.deleteByCreditCaseId(event.creditCaseId)
    }
}
