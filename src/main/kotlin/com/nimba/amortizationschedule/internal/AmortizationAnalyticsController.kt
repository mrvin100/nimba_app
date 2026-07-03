package com.nimba.amortizationschedule.internal

import com.nimba.shared.PageResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * Read-only analytics for the dossier detail screen. `/overview` returns
 * everything the screen needs in one request (summary, timeline, chart dataset,
 * progression) — optionally narrowed by a date range for the chart. The detailed
 * table is a separate, paginated endpoint loaded lazily when the user expands it.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/amortization-schedule")
class AmortizationAnalyticsController(
    private val analytics: AmortizationAnalyticsService,
) {
    @GetMapping("/overview")
    fun overview(
        @PathVariable caseId: UUID,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
    ): AmortizationOverviewResponse = analytics.overview(caseId, from, to)

    @GetMapping("/table")
    fun table(
        @PathVariable caseId: UUID,
        @RequestParam(required = false) status: PaymentStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "25") size: Int,
        @RequestParam(defaultValue = "asc") sort: String,
    ): PageResponse<AmortizationTableRow> =
        analytics.table(
            creditCaseId = caseId,
            status = status,
            page = page.coerceAtLeast(0),
            size = size.coerceIn(1, 100),
            descending = sort.equals("desc", ignoreCase = true),
        )
}
