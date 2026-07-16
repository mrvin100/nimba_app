package com.nimba.fmp.internal

import com.nimba.creditcase.CreditCaseDeleted
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
}
