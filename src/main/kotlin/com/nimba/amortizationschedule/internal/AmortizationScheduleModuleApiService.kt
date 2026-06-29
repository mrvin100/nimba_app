package com.nimba.amortizationschedule.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AmortizationScheduleModuleApiService(
    private val trades: TradeRepository,
) : AmortizationScheduleModuleApi {
    @Transactional(readOnly = true)
    override fun hasActiveTradesForCase(creditCaseId: UUID): Boolean = trades.existsByCreditCaseIdAndActiveIsTrue(creditCaseId)
}
