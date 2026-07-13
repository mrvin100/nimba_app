package com.nimba.creditcase

import java.math.BigDecimal

/** Request to replace a case's conditions-de-banque details (see [ConditionsDeBanqueInfo]). */
data class UpdateConditionsDeBanqueCommand(
    val tauxInteretPct: BigDecimal? = null,
    val fraisMiseEnPlacePct: BigDecimal? = null,
    val comEngagementPct: BigDecimal? = null,
    val fraisEtudesPct: BigDecimal? = null,
    val valeurResiduellePct: BigDecimal? = null,
    val fraisDivers: String? = null,
)
