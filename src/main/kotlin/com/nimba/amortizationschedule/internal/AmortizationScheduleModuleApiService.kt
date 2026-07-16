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
        val ordinary =
            schedule.lines
                .filterNot { it.isResidualValue }
                .sortedBy { it.numeroEcheance.toIntOrNull() ?: Int.MAX_VALUE }
        val firstOrdinary = ordinary.firstOrNull()
        val secondOrdinary = ordinary.getOrNull(1)
        val vr = schedule.lines.firstOrNull { it.isResidualValue }
        return ScheduleSummary(
            loanAmount = schedule.lines.sumOf { it.capital },
            durationMonths = ordinary.size,
            startDate = ordinary.mapNotNull { it.dateEcheance }.minOrNull(),
            endDate = ordinary.mapNotNull { it.dateEcheance }.maxOrNull(),
            totalEquipement = ordinary.sumOf { it.equipement },
            totalAssurance = ordinary.sumOf { it.assurance },
            totalTracking = ordinary.sumOf { it.tracking },
            totalImmatriculation = ordinary.sumOf { it.immatriculation },
            totalInteret = ordinary.sumOf { it.interet },
            premierLoyerTtc = firstOrdinary?.loyerTtc,
            loyerMensuelHt = (secondOrdinary ?: firstOrdinary)?.loyerHt,
            valeurResiduelle = vr?.interet,
        )
    }
}
