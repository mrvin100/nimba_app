package com.nimba.fmp

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.pv.PvGuaranteeSnapshot
import java.time.Instant
import java.util.UUID

/**
 * Read-only view of a case's FMP, safe to share across module boundaries. A
 * pure extract (design §10.4): [identite], [articulation], [garanties] and
 * [conditionsDeBanque] are read verbatim from the case's finalized PV, never
 * re-derived from the dossier's current state, so the FMP always matches the
 * décision it follows from — even if the dossier changes afterward. Only
 * [numeroPret] and [garantieRef] are new data captured on the FMP itself.
 */
data class FmpInfo(
    val id: UUID,
    val creditCaseId: UUID,
    val numeroPret: String,
    val garantieRef: String?,
    val createdBy: UUID,
    val createdAt: Instant,
    val caseNumber: String,
    val clientName: String,
    val accountNumber: String?,
    /** "DRI/{initials}" of the dossier's creating analyst. */
    val gfcEnCharge: String,
    val identite: ClientIdentityInfo,
    val articulation: ScheduleSummary,
    val garanties: List<PvGuaranteeSnapshot>,
    val conditionsDeBanque: ConditionsDeBanqueInfo,
)
