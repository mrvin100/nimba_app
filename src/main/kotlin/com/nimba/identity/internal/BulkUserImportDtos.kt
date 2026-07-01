package com.nimba.identity.internal

/** One evaluated row of a bulk import CSV, with its per-row validation verdict. */
data class BulkPreviewRow(
    val lineNumber: Long,
    val fullName: String,
    val email: String,
    val department: String?,
    val role: String?,
    val admin: Boolean,
    val valid: Boolean,
    val errors: List<String>,
)

/** Preview (or rejection) of a bulk import: the rows plus an overall verdict. */
data class BulkPreviewResponse(
    val valid: Boolean,
    val validCount: Int,
    val invalidCount: Int,
    val rows: List<BulkPreviewRow>,
)

/** Result of a committed bulk import. */
data class BulkImportResponse(
    val created: Int,
)

/**
 * Raised when a bulk import is committed while any row is invalid; carries the full
 * evaluated preview so the frontend shows exactly what to fix. Mapped to 422; no
 * account is created.
 */
class BulkImportValidationException(
    val preview: BulkPreviewResponse,
) : RuntimeException("Le fichier d'import contient des erreurs.")
