package com.nimba.creditcase

import java.math.BigDecimal

/**
 * Bank-set financing terms captured once on the dossier and reused on the Fiche
 * d'analyse's cover and §5, the PV and the FMP. Holds only the terms the TA
 * cannot derive — 1er loyer, loyer mensuel, durée and valeur résiduelle come
 * from the imported schedule (see
 * [com.nimba.amortizationschedule.ScheduleSummary]) and are never duplicated
 * here. [fraisDivers] is opaque JSON text (an array of `{label, montant}`
 * items, e.g. notification, conservation, lettre d'information, caution…) — the
 * frontend owns its exact shape, this module only persists it, exactly like the
 * analysis sheet's free-text content.
 */
data class ConditionsDeBanqueInfo(
    val tauxInteretPct: BigDecimal? = null,
    val fraisMiseEnPlacePct: BigDecimal? = null,
    val comEngagementPct: BigDecimal? = null,
    val fraisEtudesPct: BigDecimal? = null,
    val fraisDivers: String? = null,
)
