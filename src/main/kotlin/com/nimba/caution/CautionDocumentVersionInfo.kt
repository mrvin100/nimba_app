package com.nimba.caution

import java.time.Instant
import java.util.UUID

/**
 * One entry of a document's edit history: the field answers before and after
 * the change, who made it, when, and why. Newest first when listed. This is the
 * granular, per-document audit the refonte requires — a single caution among
 * several can be traced independently.
 */
data class CautionDocumentVersionInfo(
    val id: UUID,
    val contentBefore: Map<String, String>,
    val contentAfter: Map<String, String>,
    val reason: String?,
    val actor: UUID,
    val createdAt: Instant,
)
