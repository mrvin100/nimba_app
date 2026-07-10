package com.nimba.creditcase.internal

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.math.BigDecimal

/** JPA-mapped mirror of [com.nimba.creditcase.ConditionsDeBanqueInfo]; see its KDoc. */
@Embeddable
data class ConditionsDeBanque(
    @Column(name = "taux_interet_pct", precision = 6, scale = 3)
    var tauxInteretPct: BigDecimal? = null,
    @Column(name = "frais_mise_en_place_pct", precision = 6, scale = 3)
    var fraisMiseEnPlacePct: BigDecimal? = null,
    @Column(name = "com_engagement_pct", precision = 6, scale = 3)
    var comEngagementPct: BigDecimal? = null,
    @Column(name = "frais_etudes_pct", precision = 6, scale = 3)
    var fraisEtudesPct: BigDecimal? = null,
    @Column(name = "frais_divers", columnDefinition = "TEXT")
    var fraisDivers: String? = null,
)
