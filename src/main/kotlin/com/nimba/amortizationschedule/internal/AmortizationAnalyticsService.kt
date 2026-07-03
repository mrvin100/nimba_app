package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.shared.PageResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
 * Read-side analytics over the latest uploaded amortization schedule: progression,
 * summary figures, the chart dataset and the detailed table. ALL business numbers
 * are computed here (against the injected [Clock]) so the frontend only renders —
 * it never recomputes a statistic.
 *
 * The VR line has no date in the source file; its effective date derives from the
 * last ordinary échéance plus the schedule's VR offset, exactly like its trade.
 */
@Service
class AmortizationAnalyticsService(
    private val schedules: AmortizationScheduleRepository,
    private val creditCases: CreditCaseModuleApi,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun overview(
        creditCaseId: UUID,
        from: LocalDate?,
        to: LocalDate?,
    ): AmortizationOverviewResponse {
        val lines = datedLines(creditCaseId)
        val today = LocalDate.now(clock)

        val loanAmount = lines.sumOf { it.line.capital }
        val paid = lines.filter { it.status(today, lines) == PaymentStatus.PAYE }
        val paidPrincipal = paid.sumOf { it.line.capital }
        val interestPaid = paid.sumOf { it.line.interet }
        val next = lines.firstOrNull { it.status(today, lines) != PaymentStatus.PAYE }
        val completion =
            if (lines.isEmpty()) {
                BigDecimal.ZERO
            } else {
                BigDecimal(paid.size * 100).divide(BigDecimal(lines.size), 2, RoundingMode.HALF_UP)
            }

        return AmortizationOverviewResponse(
            summary =
                AmortizationSummary(
                    loanAmount = loanAmount,
                    paidPrincipal = paidPrincipal,
                    remainingPrincipal = loanAmount - paidPrincipal,
                    interestPaid = interestPaid,
                    durationMonths = lines.count { !it.line.isResidualValue },
                    nextPaymentDate = next?.date,
                    nextPaymentAmount = next?.line?.loyerTtc,
                ),
            timeline =
                AmortizationTimeline(
                    startDate = lines.firstOrNull()?.date,
                    endDate = lines.lastOrNull()?.date,
                    today = today,
                    currentPeriod = paid.size,
                    remainingPeriods = lines.size - paid.size,
                ),
            chart = chart(lines, loanAmount, from, to),
            status =
                AmortizationProgress(
                    completedPayments = paid.size,
                    remainingPayments = lines.size - paid.size,
                    completion = completion,
                ),
        )
    }

    @Transactional(readOnly = true)
    fun table(
        creditCaseId: UUID,
        status: PaymentStatus?,
        page: Int,
        size: Int,
        descending: Boolean,
    ): PageResponse<AmortizationTableRow> {
        val lines = datedLines(creditCaseId)
        val today = LocalDate.now(clock)
        val rows =
            lines
                .map { dated ->
                    AmortizationTableRow(
                        period = dated.line.numeroEcheance,
                        date = dated.date,
                        capital = dated.line.capital,
                        interet = dated.line.interet,
                        mensualite = dated.line.loyerTtc,
                        capitalRestantDu = dated.line.capitalRestantDu,
                        status = dated.status(today, lines),
                    )
                }.filter { status == null || it.status == status }
                .let { if (descending) it.reversed() else it }

        val fromIndex = (page * size).coerceAtMost(rows.size)
        val toIndex = (fromIndex + size).coerceAtMost(rows.size)
        val totalPages = if (rows.isEmpty()) 0 else (rows.size + size - 1) / size
        return PageResponse(
            content = rows.subList(fromIndex, toIndex),
            page = page,
            size = size,
            totalElements = rows.size.toLong(),
            totalPages = totalPages,
            hasNext = toIndex < rows.size,
            hasPrevious = page > 0,
        )
    }

    /** Latest schedule's lines with their effective dates, or 404 when nothing was imported. */
    private fun datedLines(creditCaseId: UUID): List<DatedLine> {
        creditCases.getOrThrow(creditCaseId)
        val schedule =
            schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun échéancier importé pour ce dossier.")
        val lastOrdinaryDate = schedule.lines.lastOrNull { !it.isResidualValue }?.dateEcheance
        return schedule.lines.map { line ->
            val date =
                line.dateEcheance
                    ?: if (line.isResidualValue) lastOrdinaryDate?.plusMonths(schedule.vrOffsetMonths.toLong()) else null
            DatedLine(line, date)
        }
    }

    private fun chart(
        lines: List<DatedLine>,
        loanAmount: BigDecimal,
        from: LocalDate?,
        to: LocalDate?,
    ): List<AmortizationChartPoint> {
        fun percentage(paid: BigDecimal): BigDecimal =
            if (loanAmount.signum() == 0) BigDecimal.ZERO else paid.multiply(BigDecimal(100)).divide(loanAmount, 2, RoundingMode.HALF_UP)

        var paidCumulative = BigDecimal.ZERO
        val points =
            mutableListOf(AmortizationChartPoint(0, lines.firstOrNull()?.date, loanAmount, BigDecimal.ZERO, BigDecimal.ZERO))
        lines.forEachIndexed { index, dated ->
            paidCumulative += dated.line.capital
            points +=
                AmortizationChartPoint(
                    period = index + 1,
                    date = dated.date,
                    remainingCapital = dated.line.capitalRestantDu ?: (loanAmount - paidCumulative),
                    paidCapital = paidCumulative,
                    paidPercentage = percentage(paidCumulative),
                )
        }
        // Period filter requested by the screen's date-range selector; the baseline
        // point follows the lower bound so the curve always starts at the range.
        return points.filter { point ->
            (from == null || point.date == null || !point.date.isBefore(from)) &&
                (to == null || point.date == null || !point.date.isAfter(to))
        }
    }

    private data class DatedLine(
        val line: AmortizationScheduleLine,
        val date: LocalDate?,
    ) {
        /**
         * PAYE strictly before today; the first unsettled échéance is EN_COURS
         * (due today or the next one coming), everything after is A_VENIR.
         */
        fun status(
            today: LocalDate,
            all: List<DatedLine>,
        ): PaymentStatus {
            if (date != null && date.isBefore(today)) return PaymentStatus.PAYE
            val firstUnsettled = all.firstOrNull { it.date == null || !it.date.isBefore(today) }
            return if (this === firstUnsettled) PaymentStatus.EN_COURS else PaymentStatus.A_VENIR
        }
    }
}
