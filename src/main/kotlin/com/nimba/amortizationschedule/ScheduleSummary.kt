package com.nimba.amortizationschedule

import java.math.BigDecimal
import java.time.LocalDate

/**
 * The handful of TA figures other modules are allowed to reuse — today, prefilling
 * the Fiche d'analyse header. Deliberately smaller than the internal analytics
 * summary (no payment-status breakdown): a public API surface stays only as wide
 * as an actual cross-module need.
 */
data class ScheduleSummary(
    /** Total financed principal (capital of every échéance, VR included). */
    val loanAmount: BigDecimal,
    /** Number of ordinary (monthly) échéances, VR excluded. */
    val durationMonths: Int,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
)
