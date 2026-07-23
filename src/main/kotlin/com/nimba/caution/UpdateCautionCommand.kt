package com.nimba.caution

/**
 * Replaces a document's field answers wholesale. Refused when the dossier is
 * locked (FINALISE); allowed while BROUILLON or during a prorogation. [reason]
 * is journaled in the document's history (mandatory in practice for a change
 * made during a prorogation).
 */
data class UpdateCautionCommand(
    val content: Map<String, String>,
    val reason: String? = null,
)
