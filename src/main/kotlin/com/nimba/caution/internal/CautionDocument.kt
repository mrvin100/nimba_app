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
import jakarta.persistence.Version
import java.time.Instant
import java.util.UUID

/**
 * One document generated within a dossier (Caution de Soumission, Attestation
 * de Capacité Financière, Attestation de Facilité de Crédit, Avenant de
 * Prorogation…). [clientId] references the client module's aggregate by id only
 * — no JPA relationship crosses the module boundary. [referenceNumber]
 * (format `{sequence}-{documentType.code}-{date}`; does not embed the client's
 * matricule — see [CautionClientSnapshot] for the client identity actually
 * captured) and [sequence] are assigned once at creation and stay editable
 * (together) while the document is still editable, EXCEPT a PRO: it carries no
 * series of its own (it is the same Caution de Soumission with revised dates),
 * so it copies its origin SMS's [referenceNumber] and [sequence] verbatim (see
 * [com.nimba.caution.internal.CautionModuleApiService.create]). Frozen for
 * good once finalized, same as the rest of the document. The table keeps its
 * historical name (`caution`).
 */
@Entity
@Table(name = "caution")
class CautionDocument(
    @Column(name = "client_id", nullable = false, updatable = false)
    val clientId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false)
    val documentType: CautionDocumentType,
    @Column(name = "reference_number", nullable = false)
    var referenceNumber: String,
    /**
     * The 5-digit series number embedded in [referenceNumber], entered by the
     * analyst (a PRO copies its origin's). Editable together with
     * [referenceNumber] while the document is still editable, frozen once
     * finalized.
     */
    @Column(name = "sequence", nullable = false)
    var sequence: Int,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    /** Optimistic-lock counter (JPA-managed): a concurrent edit of the same document fails with a 409 rather than silently overwriting. */
    @Version
    @Column(name = "lock_version", nullable = false)
    var lockVersion: Long = 0

    /** The dossier this document belongs to, or null for a legacy document created before dossiers (attached by the V32 migration). */
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
