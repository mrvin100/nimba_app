package com.nimba.amortizationschedule.internal

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Read-model returned to the dossier detail screen (one request renders the whole
 * overview). Every figure is computed server-side — the frontend never derives a
 * business number itself. Amounts are exact [BigDecimal]; the schedule carries no
 * annual rate, so none is invented here.
 */
data class AmortizationOverviewResponse(
    val summary: AmortizationSummary,
    val timeline: AmortizationTimeline,
    val chart: List<AmortizationChartPoint>,
    val status: AmortizationProgress,
)

data class AmortizationSummary(
    /** Total financed principal (capital of every échéance, VR included). */
    val loanAmount: BigDecimal,
    val paidPrincipal: BigDecimal,
    val remainingPrincipal: BigDecimal,
    val interestPaid: BigDecimal,
    /** Number of ordinary (monthly) échéances, VR excluded. */
    val durationMonths: Int,
    val nextPaymentDate: LocalDate?,
    val nextPaymentAmount: BigDecimal?,
)

data class AmortizationTimeline(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val today: LocalDate,
    /** Number of settled périodes — where the "today" marker sits on the chart. */
    val currentPeriod: Int,
    val remainingPeriods: Int,
)

data class AmortizationChartPoint(
    /** 0 = situation before the first payment (full principal outstanding). */
    val period: Int,
    val date: LocalDate?,
    val remainingCapital: BigDecimal,
    val paidCapital: BigDecimal,
    /** Share of the principal settled at this point, 2 decimals (tooltip). */
    val paidPercentage: BigDecimal,
)

data class AmortizationProgress(
    val completedPayments: Int,
    val remainingPayments: Int,
    /** Percentage of settled payments, 2 decimals (e.g. 41.67). */
    val completion: BigDecimal,
)

/** Payment state of one échéance relative to today. */
enum class PaymentStatus { PAYE, EN_COURS, A_VENIR }

/**
 * Sortable columns of the detailed table. PERIODE is the schedule's own order
 * (the CSV line order — "1".."24" then "VR", which no lexicographic sort of the
 * string could reproduce); the others compare the row's values, nulls last.
 */
enum class TableSortField { PERIODE, DATE, CAPITAL, INTERET, MENSUALITE, CAPITAL_RESTANT }

/** Row of the lazily-loaded detailed table. */
data class AmortizationTableRow(
    /** Numero as uploaded ("1".."24" or "VR"). */
    val period: String,
    val date: LocalDate?,
    val capital: BigDecimal,
    val interet: BigDecimal,
    /** Mensualité (loyer TTC). */
    val mensualite: BigDecimal,
    val capitalRestantDu: BigDecimal?,
    val status: PaymentStatus,
)
