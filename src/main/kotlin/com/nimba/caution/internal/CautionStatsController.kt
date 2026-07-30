package com.nimba.caution.internal

import com.nimba.caution.DossierStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class DossierStatusCount(
    val status: DossierStatus,
    val count: Long,
)

data class CautionStatsResponse(
    val total: Long,
    val byStatus: List<DossierStatusCount>,
)

/**
 * Aggregate caution-dossier counts for the admin dashboard. Under the admin path
 * tree, so it requires ROLE_ADMIN (security config). Reports the total and a
 * breakdown by lifecycle status (this is the engagement archetype's half of the
 * cross-product picture — see `creditcase`'s own `/admin/stats/dossiers` for the
 * financement breakdown).
 */
@RestController
@RequestMapping("/admin/stats/cautions")
class CautionStatsController(
    private val dossiers: CautionDossierRepository,
) {
    @GetMapping
    fun get(): CautionStatsResponse =
        CautionStatsResponse(
            total = dossiers.count(),
            byStatus = DossierStatus.entries.map { DossierStatusCount(it, dossiers.countByStatus(it)) },
        )
}
