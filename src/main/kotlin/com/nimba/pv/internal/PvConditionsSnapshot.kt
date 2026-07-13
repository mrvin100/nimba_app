package com.nimba.pv.internal

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal

/** Frozen mirror of [com.nimba.creditcase.ConditionsDeBanqueInfo] at PV finalization. */
@Embeddable
data class PvConditionsSnapshot(
    @Column(name = "snap_taux_interet_pct", precision = 6, scale = 3)
    val tauxInteretPct: BigDecimal? = null,
    @Column(name = "snap_frais_mise_en_place_pct", precision = 6, scale = 3)
    val fraisMiseEnPlacePct: BigDecimal? = null,
    @Column(name = "snap_com_engagement_pct", precision = 6, scale = 3)
    val comEngagementPct: BigDecimal? = null,
    @Column(name = "snap_frais_etudes_pct", precision = 6, scale = 3)
    val fraisEtudesPct: BigDecimal? = null,
    @Column(name = "snap_valeur_residuelle_pct", precision = 6, scale = 3)
    val valeurResiduellePct: BigDecimal? = null,
    @Column(name = "snap_frais_divers", columnDefinition = "TEXT")
    val fraisDivers: String? = null,
)
