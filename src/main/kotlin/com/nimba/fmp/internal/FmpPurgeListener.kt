package com.nimba.fmp.internal

import com.nimba.creditcase.CreditCaseDeleted
import com.nimba.creditcase.CreditCaseDocumentResetRequested
import com.nimba.creditcase.ResettableDocument
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Purges a case's FMP when an administrator deletes the case. */
@Component
class FmpPurgeListener(
    private val fmps: FmpRepository,
) {
    @EventListener
    fun purge(event: CreditCaseDeleted) {
        fmps.deleteByCreditCaseId(event.creditCaseId)
    }

    /** The Settings tab's « réinitialiser la FMP » — same wipe, dossier kept (design §12.3). */
    @EventListener
    fun reset(event: CreditCaseDocumentResetRequested) {
        if (event.document != ResettableDocument.FMP) return
        purge(CreditCaseDeleted(event.creditCaseId))
    }
}
