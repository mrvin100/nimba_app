package com.nimba.pv

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo

/**
 * The dossier-level data frozen onto a PV at finalization — identité client,
 * articulation du financement (the TA breakdown), garanties, conditions de
 * banque, and the FA's own points forts/faibles, exactly as they stood at that
 * moment. Null on a DRAFT PV, which renders these same sections live from the
 * dossier (and the FA) instead.
 */
data class PvSnapshot(
    val identite: ClientIdentityInfo,
    val articulation: ScheduleSummary,
    val garanties: List<PvGuaranteeSnapshot>,
    val conditionsDeBanque: ConditionsDeBanqueInfo,
    /** Read verbatim from the FA's `CONCLUSION_POINTS_FORTS` section; null if never filled in. */
    val pointsForts: String?,
    /** Read verbatim from the FA's `CONCLUSION_POINTS_FAIBLES` section; null if never filled in. */
    val pointsFaibles: String?,
)
