package com.nimba.caution.internal

import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionStatus
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
import java.util.UUID

/**
 * One generated banking document (Caution de Soumission, Attestation de
 * Capacité Financière, later Avance sur Démarrage...). [clientId] references
 * the client module's aggregate by id only — no JPA relationship crosses the
 * module boundary. [referenceNumber] is assigned once at creation (design
 * §numbering: a single global sequence, format
 * `{sequence}-{matricule}-{documentType.code}-{date}`) and never changes.
 */
@Entity
@Table(name = "caution")
class Caution(
    @Column(name = "client_id", nullable = false, updatable = false)
    val clientId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    val documentType: CautionDocumentType,
    @Column(name = "reference_number", nullable = false, unique = true, updatable = false)
    val referenceNumber: String,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** The dossier this document belongs to, or null when created standalone (every pre-dossier document stays valid). */
    @Column(name = "dossier_id")
    var dossierId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CautionStatus = CautionStatus.DRAFT

    /** JSON object of every shared + type-specific field answer, keyed by `CautionFieldDefinition.key`. */
    @Column(name = "content_json", nullable = false, columnDefinition = "TEXT")
    var contentJson: String = "{}"

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "finalized_at")
    var finalizedAt: Instant? = null

    /**
     * Nullable while DRAFT (nothing to snapshot yet) — Hibernate reloads an
     * `@Embedded` value object as null once every one of its columns is null,
     * matching reality: no snapshot exists before finalization.
     */
    @Embedded
    var clientSnapshot: CautionClientSnapshot? = null
}
