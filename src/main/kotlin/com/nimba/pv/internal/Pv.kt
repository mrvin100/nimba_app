package com.nimba.pv.internal

import com.nimba.pv.PvStatus
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A dossier's PV — modeled per dossier, séance-dated (design §10.3). Drafted by
 * the DCM once the comité has approved the dossier, then finalized: from that
 * point on it is immutable and renders its frozen [identitySnapshot] /
 * [articulationSnapshot] / [conditionsSnapshot] (plus [PvGuaranteeSnapshotRow]
 * rows) instead of the dossier's live data. [creditCaseId] references the
 * credit-case module's aggregate by id only — no JPA relationship crosses the
 * module boundary.
 */
@Entity
@Table(name = "pv")
class Pv(
    @Column(name = "credit_case_id", nullable = false, updatable = false, unique = true)
    val creditCaseId: UUID,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
    @Column(name = "seance_date", nullable = false)
    var seanceDate: LocalDate,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: PvStatus = PvStatus.DRAFT

    @Column(name = "rapporteur")
    var rapporteur: String? = null

    @Column(name = "president")
    var president: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "finalized_at")
    var finalizedAt: Instant? = null

    /**
     * Nullable while DRAFT (nothing to snapshot yet) — Hibernate reloads an
     * `@Embedded` value object as null once every one of its columns is null, so
     * this also matches reality: no snapshot exists before finalization. Read
     * these three snapshots together; either all are set (FINAL) or none are.
     */
    @Embedded
    var identitySnapshot: PvIdentitySnapshot? = null

    @Embedded
    var articulationSnapshot: PvArticulationSnapshot? = null

    @Embedded
    var conditionsSnapshot: PvConditionsSnapshot? = null

    /**
     * Points forts/faibles frozen at finalization, read from the FA's own
     * `CONCLUSION_POINTS_FORTS`/`CONCLUSION_POINTS_FAIBLES` sections — never
     * typed on the PV itself (real-document analysis, 2026-07-13): the
     * analyst's judgment lives on the FA, the PV only reuses it.
     */
    @Column(name = "snap_points_forts", columnDefinition = "TEXT")
    var snapPointsForts: String? = null

    @Column(name = "snap_points_faibles", columnDefinition = "TEXT")
    var snapPointsFaibles: String? = null
}
