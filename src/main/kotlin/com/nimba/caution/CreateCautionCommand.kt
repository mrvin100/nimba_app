package com.nimba.caution

import java.util.UUID

/**
 * Opens a caution in DRAFT and assigns its reference number immediately (the
 * number is part of the document's identity from the start, not a
 * print-time detail — unlike the PV, where only the *content* is frozen at
 * finalization). [content] carries every shared and type-specific field's
 * answer, keyed by [CautionFieldDefinition.key].
 */
data class CreateCautionCommand(
    val clientId: UUID,
    val documentType: CautionDocumentType,
    val content: Map<String, String>,
    val createdBy: UUID,
)
