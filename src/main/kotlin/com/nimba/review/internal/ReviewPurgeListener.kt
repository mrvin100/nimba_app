package com.nimba.review.internal

import com.nimba.creditcase.CreditCaseDeleted
import com.nimba.creditcase.CreditCaseDocumentResetRequested
import com.nimba.creditcase.ResettableDocument
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

    /** Resetting the FA also wipes the threads anchored on its sections (design §12.3). */
    @EventListener
    fun reset(event: CreditCaseDocumentResetRequested) {
        if (event.document != ResettableDocument.FICHE_ANALYSE) return
        purge(CreditCaseDeleted(event.creditCaseId))
    }
}
