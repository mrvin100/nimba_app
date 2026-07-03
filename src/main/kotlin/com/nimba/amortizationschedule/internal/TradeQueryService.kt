package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Read access to a case's trades. Returns only the active generation (NIMBA-24),
 * ordered by due date. An empty list is a valid result (no trades generated yet);
 * an unknown case is a 404.
 */
@Service
class TradeQueryService(
    private val trades: TradeRepository,
    private val creditCases: CreditCaseModuleApi,
) {
    @Transactional(readOnly = true)
    fun activeTrades(creditCaseId: UUID): List<Trade> {
        creditCases.getOrThrow(creditCaseId)
        return trades.findByCreditCaseIdAndActiveIsTrueOrderByDueDateAsc(creditCaseId)
    }
}
