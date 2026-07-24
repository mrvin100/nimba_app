package com.nimba.caution

import java.time.Instant
import java.util.UUID

/** Frozen mirror of the issuing client at finalization — see [CautionInfo.clientSnapshot]'s KDoc. */
data class CautionClientSnapshotInfo(
    val matricule: String,
    val raisonSociale: String,
    val sigle: String?,
    val adressePhysique: String?,
    val rccm: String?,
    val accountNumber: String?,
    val agence: String?,
)

/** Read-only view of a caution, safe to share across module boundaries. */
data class CautionInfo(
    val id: UUID,
    val clientId: UUID,
    /** The dossier this document belongs to, or null for a legacy standalone document. */
    val dossierId: UUID?,
    val documentType: CautionDocumentType,
    val referenceNumber: String,
    val status: CautionStatus,
    val content: Map<String, String>,
    /**
     * The client's identity fields as they stood at finalization — null while
     * DRAFT (rendered live from the client record instead), so an already-issued
     * legal document never silently changes if the client's own record is
     * edited afterward. Same pattern as the PV's snapshot.
     */
    val clientSnapshot: CautionClientSnapshotInfo?,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finalizedAt: Instant?,
)
