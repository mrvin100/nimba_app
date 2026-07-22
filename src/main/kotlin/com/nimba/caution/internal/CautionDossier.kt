package com.nimba.caution.internal

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

/**
 * A dossier de caution de soumission: the unit of work grouping every document
 * generated for one client request against one appel d'offres (possibly across
 * several lots). [clientId] references the client module's aggregate by id only
 * — no JPA relationship crosses the module boundary. Member documents point
 * back through [Caution.dossierId]; the dossier itself carries only the shared
 * market context in [contentJson], keyed by `CautionFieldDefinition.key`.
 */
@Entity
@Table(name = "caution_dossier")
class CautionDossier(
    @Column(name = "client_id", nullable = false, updatable = false)
    val clientId: UUID,
    @Column(name = "reference_number", nullable = false, unique = true, updatable = false)
    val referenceNumber: String,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: DossierStatus = DossierStatus.OPEN

    /** JSON object of the shared market context, keyed by `CautionFieldDefinition.key`. */
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    var contentJson: String = "{}"

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
