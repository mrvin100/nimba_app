package com.nimba.fmp.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A dossier's FMP — a pure extract generated once a PV is finalized (design
 * §10.4). Unlike the PV, there is no draft/edit lifecycle: [numeroPret] and
 * [garantieRef] are the only new data, captured at generation and never
 * changed afterward; everything else is read live from the PV each time (see
 * [com.nimba.fmp.FmpInfo]'s KDoc), so nothing needs to be stored twice.
 * [creditCaseId] references the credit-case module's aggregate by id only —
 * no JPA relationship crosses the module boundary.
 */
@Entity
@Table(name = "fmp")
class Fmp(
    @Column(name = "credit_case_id", nullable = false, updatable = false, unique = true)
    val creditCaseId: UUID,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
    @Column(name = "numero_pret", nullable = false, updatable = false)
    val numeroPret: String,
    @Column(name = "garantie_ref", updatable = false)
    val garantieRef: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
