package com.nimba.analysissheet

import java.time.Instant
import java.util.UUID

/**
 * One uploaded figure of an IMAGE-type FA section, as served with the section
 * list (metadata only — the binary is downloaded through its own endpoint).
 * Order follows the upload order, which is also the export's embedding order.
 */
data class FaSectionImageInfo(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val caption: String?,
    val uploadedAt: Instant,
)
