package com.nimba.amortizationschedule

import java.math.BigDecimal
import java.time.LocalDate

/**
 * The handful of TA figures other modules are allowed to reuse — prefilling the
 * Fiche d'analyse header and the dossier's "articulation du financement" (which
 * has no storage of its own: the TA is its single source of truth). Deliberately
 * smaller than the internal analytics summary (no payment-status breakdown): a
 * public API surface stays only as wide as an actual cross-module need.
 */
data class ScheduleSummary(
    /** Total financed principal (capital of every échéance, VR included). */
    val loanAmount: BigDecimal,
    /** Number of ordinary (monthly) échéances, VR excluded. */
    val durationMonths: Int,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    /** Sum of the "équipement" column across ordinary échéances. */
    val totalEquipement: BigDecimal,
    /** Sum of the "assurance" column across ordinary échéances. */
    val totalAssurance: BigDecimal,
    /** Sum of the "tracking" column across ordinary échéances. */
    val totalTracking: BigDecimal,
    /** Sum of the "immatriculation" column across ordinary échéances. */
    val totalImmatriculation: BigDecimal,
    /** Sum of the "intérêt" column across ordinary échéances — the FA §3.4's "intérêts sur durée". */
    val totalInteret: BigDecimal,
    /** The first ordinary échéance's loyer TTC — the FA cover's "1er loyer TTC". */
    val premierLoyerTtc: BigDecimal?,
    /**
     * The recurring monthly loyer HT — read from the second ordinary échéance
     * (the first can differ, e.g. an advance folded into it), falling back to the
     * first when the schedule has only one ordinary line.
     */
    val loyerMensuelHt: BigDecimal?,
    /**
     * The VR line's residual-value amount. Stored in that line's `interet` column
     * (see [com.nimba.amortizationschedule.internal.AmortizationScheduleConsistencyChecker]
     * for why the VR line's structure differs from ordinary lines); null when the
     * schedule has no VR line.
     */
    val valeurResiduelle: BigDecimal?,
)
