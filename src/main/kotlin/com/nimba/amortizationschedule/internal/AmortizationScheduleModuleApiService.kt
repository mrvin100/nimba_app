package com.nimba.amortizationschedule.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.amortizationschedule.ScheduleSummary
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AmortizationScheduleModuleApiService(
    private val schedules: AmortizationScheduleRepository,
    private val trades: TradeRepository,
) : AmortizationScheduleModuleApi {
    @Transactional(readOnly = true)
    override fun hasActiveTradesForCase(creditCaseId: UUID): Boolean = trades.existsByCreditCaseIdAndActiveIsTrue(creditCaseId)

    @Transactional(readOnly = true)
    override fun hasScheduleForCase(creditCaseId: UUID): Boolean =
        schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(creditCaseId) != null

    @Transactional(readOnly = true)
    override fun scheduleSummary(creditCaseId: UUID): ScheduleSummary? {
        val schedule = schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(creditCaseId) ?: return null
        val ordinary = schedule.lines.filterNot { it.isResidualValue }
        return ScheduleSummary(
            loanAmount = schedule.lines.sumOf { it.capital },
            durationMonths = ordinary.size,
            startDate = ordinary.mapNotNull { it.dateEcheance }.minOrNull(),
            endDate = ordinary.mapNotNull { it.dateEcheance }.maxOrNull(),
        )
    }
}
