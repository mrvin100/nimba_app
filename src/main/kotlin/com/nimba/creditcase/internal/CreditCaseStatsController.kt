package com.nimba.creditcase.internal

import com.nimba.creditcase.CreditCaseStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreditCaseStatusCount(
    val status: CreditCaseStatus,
    val count: Long,
)

data class CreditCaseStatsResponse(
    val total: Long,
    val byStatus: List<CreditCaseStatusCount>,
)

/**
 * Aggregate credit-case counts for the admin dashboard. Under the admin path tree, so
 * it requires ROLE_ADMIN (security config). Reports the total and a breakdown by
 * phase-1 status (awaiting schedule vs trades generated).
 */
@RestController
@RequestMapping("/admin/stats/dossiers")
class CreditCaseStatsController(
    private val creditCases: CreditCaseRepository,
) {
    @GetMapping
    fun get(): CreditCaseStatsResponse =
        CreditCaseStatsResponse(
            total = creditCases.count(),
            byStatus = CreditCaseStatus.entries.map { CreditCaseStatusCount(it, creditCases.countByStatus(it)) },
        )
}
