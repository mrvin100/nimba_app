package com.nimba.pv.internal

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal

/** Frozen mirror of [com.nimba.amortizationschedule.ScheduleSummary] at PV finalization. */
@Embeddable
data class PvArticulationSnapshot(
    @Column(name = "snap_loan_amount", precision = 20, scale = 4)
    val loanAmount: BigDecimal? = null,
    @Column(name = "snap_duration_months")
    val durationMonths: Int? = null,
    @Column(name = "snap_total_equipement", precision = 20, scale = 4)
    val totalEquipement: BigDecimal? = null,
    @Column(name = "snap_total_assurance", precision = 20, scale = 4)
    val totalAssurance: BigDecimal? = null,
    @Column(name = "snap_total_tracking", precision = 20, scale = 4)
    val totalTracking: BigDecimal? = null,
    @Column(name = "snap_total_immatriculation", precision = 20, scale = 4)
    val totalImmatriculation: BigDecimal? = null,
    @Column(name = "snap_total_interet", precision = 20, scale = 4)
    val totalInteret: BigDecimal? = null,
    @Column(name = "snap_premier_loyer_ttc", precision = 20, scale = 4)
    val premierLoyerTtc: BigDecimal? = null,
    @Column(name = "snap_loyer_mensuel_ht", precision = 20, scale = 4)
    val loyerMensuelHt: BigDecimal? = null,
    @Column(name = "snap_valeur_residuelle", precision = 20, scale = 4)
    val valeurResiduelle: BigDecimal? = null,
)
