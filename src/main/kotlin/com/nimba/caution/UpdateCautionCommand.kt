package com.nimba.caution

/**
 * Replaces a document's field answers wholesale. Refused when the dossier is
 * locked (FINALISE); allowed while BROUILLON or during a prorogation. [reason]
 * is journaled in the document's history (mandatory in practice for a change
 * made during a prorogation). [sequence] lets the analyst correct the
 * reference number's series value while still editable; null leaves it
 * unchanged. Ignored for a PRO, whose reference always tracks its origin SMS.
 */
data class UpdateCautionCommand(
    val content: Map<String, String>,
    val sequence: Int? = null,
    val reason: String? = null,
)
