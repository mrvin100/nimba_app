package com.nimba.caution

import java.util.UUID

/**
 * Opens a caution in DRAFT and assigns its reference number immediately (the
 * number is part of the document's identity from the start, not a
 * print-time detail — unlike the PV, where only the *content* is frozen at
 * finalization). [content] carries every shared and type-specific field's
 * answer, keyed by [CautionFieldDefinition.key].
 *
 * [sequence] is the analyst-entered number in [documentType]'s own series
 * (SMS, ACF and AFC each run independently); it is ignored for a
 * [CautionDocumentType.PRO], which instead copies its [originDocumentId]'s
 * reference number and sequence verbatim — a PRO is the same Caution de
 * Soumission with revised dates, not a new series member.
 */
data class CreateCautionCommand(
    val clientId: UUID,
    val documentType: CautionDocumentType,
    val content: Map<String, String>,
    val createdBy: UUID,
    val sequence: Int? = null,
    /** Required for a PRO: the SMS document (in the same dossier) it prorogates. */
    val originDocumentId: UUID? = null,
    /** The dossier this document belongs to, or null when created standalone. */
    val dossierId: UUID? = null,
)
