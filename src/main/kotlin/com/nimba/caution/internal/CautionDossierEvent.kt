package com.nimba.caution.internal

import com.nimba.caution.DossierAction
import com.nimba.caution.DossierStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** One immutable entry of a dossier's lifecycle journal (finalize / proroge / refinalize). */
@Entity
@Table(name = "caution_dossier_event")
class CautionDossierEvent(
    @Column(name = "dossier_id", nullable = false, updatable = false)
    val dossierId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    val action: DossierAction,
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false)
    val fromStatus: DossierStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    val toStatus: DossierStatus,
    @Column(name = "reason", updatable = false)
    val reason: String?,
    @Column(name = "actor", nullable = false, updatable = false)
    val actor: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
