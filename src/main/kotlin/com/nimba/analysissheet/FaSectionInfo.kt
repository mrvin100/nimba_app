package com.nimba.analysissheet

import java.time.Instant

/**
 * One section of a case's FA, resolved for display: its registry metadata plus
 * whatever content was stored for it. [contentJson] and [updatedAt] stay null
 * for COMPUTED and BOUND sections (never persisted — see [FaSectionType]) and
 * for an editable section nothing has been saved to yet.
 */
data class FaSectionInfo(
    val key: FaSectionKey,
    val pilier: FaPilier,
    val type: FaSectionType,
    val label: String,
    val contentJson: String? = null,
    val updatedAt: Instant? = null,
)
