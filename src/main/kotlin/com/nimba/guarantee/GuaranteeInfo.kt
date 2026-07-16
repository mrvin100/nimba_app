package com.nimba.guarantee

import java.time.Instant
import java.util.UUID

/** Read-only view of a guarantee's file, safe to share across module boundaries. */
data class GuaranteeAttachmentInfo(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: Instant,
)

/**
 * Read-only view of a dossier's guarantee, safe to share across module boundaries.
 * Bound into the Fiche d'analyse (§4.2/§5), the PV and the FMP.
 */
data class GuaranteeInfo(
    val id: UUID,
    val creditCaseId: UUID,
    val kind: GuaranteeKind,
    val description: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attachments: List<GuaranteeAttachmentInfo>,
)

/** A guarantee's file, loaded for download. */
data class GuaranteeAttachmentObject(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)
