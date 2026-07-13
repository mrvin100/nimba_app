package com.nimba.pv

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo

/**
 * The dossier-level data frozen onto a PV at finalization — identité client,
 * articulation du financement (the TA breakdown), garanties and conditions de
 * banque, exactly as they stood at that moment. Null on a DRAFT PV, which
 * renders these same sections live from the dossier instead.
 */
data class PvSnapshot(
    val identite: ClientIdentityInfo,
    val articulation: ScheduleSummary,
    val garanties: List<PvGuaranteeSnapshot>,
    val conditionsDeBanque: ConditionsDeBanqueInfo,
)
