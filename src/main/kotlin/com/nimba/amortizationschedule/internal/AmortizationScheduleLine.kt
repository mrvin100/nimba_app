package com.nimba.amortizationschedule.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * One row of an amortization schedule. [numeroEcheance] is text because it may be
 * the literal "VR" (valeur résiduelle) in addition to a positive integer. All
 * monetary fields are exact [BigDecimal] (NUMERIC), never floating point.
 * [dateEcheance] is nullable for the VR line, whose trade date derives from the
 * last ordinary échéance. [capitalRestantDu] is collected for a future financial
 * statements feature and is not used to generate trades.
 */
@Entity
@Table(name = "amortization_schedule_line")
class AmortizationScheduleLine(
    @Column(name = "numero_echeance", nullable = false)
    val numeroEcheance: String,
    @Column(name = "date_echeance")
    val dateEcheance: LocalDate?,
    @Column(name = "interet", nullable = false, precision = 20, scale = 4)
    val interet: BigDecimal,
    @Column(name = "equipement", nullable = false, precision = 20, scale = 4)
    val equipement: BigDecimal,
    @Column(name = "assurance", nullable = false, precision = 20, scale = 4)
    val assurance: BigDecimal,
    @Column(name = "tracking", nullable = false, precision = 20, scale = 4)
    val tracking: BigDecimal,
    @Column(name = "immatriculation", nullable = false, precision = 20, scale = 4)
    val immatriculation: BigDecimal,
    @Column(name = "capital", nullable = false, precision = 20, scale = 4)
    val capital: BigDecimal,
    @Column(name = "loyer_ht", nullable = false, precision = 20, scale = 4)
    val loyerHt: BigDecimal,
    @Column(name = "taxes", nullable = false, precision = 20, scale = 4)
    val taxes: BigDecimal,
    @Column(name = "loyer_ttc", nullable = false, precision = 20, scale = 4)
    val loyerTtc: BigDecimal,
    @Column(name = "capital_restant_du", precision = 20, scale = 4)
    val capitalRestantDu: BigDecimal?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @ManyToOne
    @JoinColumn(name = "schedule_id", nullable = false)
    var schedule: AmortizationSchedule? = null

    val isResidualValue: Boolean
        get() = numeroEcheance.equals("VR", ignoreCase = true)
}
