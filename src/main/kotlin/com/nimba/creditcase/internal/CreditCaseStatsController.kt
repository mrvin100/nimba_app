package com.nimba.creditcase.internal

import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreditCaseStatusCount(
    val status: CreditCaseStatus,
    val count: Long,
)

data class ProductTypeCount(
    val productType: ProductType,
    val count: Long,
)

data class CreditCaseStatsResponse(
    val total: Long,
    val byStatus: List<CreditCaseStatusCount>,
    val byProductType: List<ProductTypeCount>,
)

/**
 * Aggregate credit-case counts for the admin dashboard. Under the admin path tree, so
 * it requires ROLE_ADMIN (security config). Reports the total, a breakdown by phase-1
 * status (awaiting schedule vs trades generated), and by product (financement's half
 * of the cross-product picture — see `caution`'s and `workflow`'s own admin stats
 * endpoints for the caution and cross-directorate breakdowns; the admin frontend
 * composes all three rather than this backend aggregating across module boundaries).
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
            byProductType = ProductType.entries.map { ProductTypeCount(it, creditCases.countByProductType(it)) },
        )
}
