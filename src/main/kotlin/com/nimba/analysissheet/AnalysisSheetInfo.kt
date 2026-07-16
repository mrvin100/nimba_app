package com.nimba.analysissheet

import com.nimba.creditcase.FaVariant
import java.time.Instant
import java.util.UUID

/** Read-only view of a case's Fiche d'analyse, safe to share across module boundaries. */
data class AnalysisSheetInfo(
    val id: UUID,
    val creditCaseId: UUID,
    val faVariant: FaVariant,
    val status: AnalysisSheetStatus,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val publishedAt: Instant?,
)
