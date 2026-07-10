package com.nimba.analysissheet.internal

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.analysissheet.AnalysisSheetInfo
import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.creditcase.FaVariant
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class AnalysisSheetContentRequest(
    @field:Size(max = 20000, message = "Le contenu ne peut dépasser 20 000 caractères")
    val content: String?,
)

data class AnalysisSheetResponse(
    val id: UUID,
    val creditCaseId: UUID,
    val faVariant: FaVariant,
    val status: AnalysisSheetStatus,
    val content: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val publishedAt: Instant?,
    /** TA figures reused to prefill the sheet's header; null if none were computable. */
    val taSummary: ScheduleSummary?,
)

internal fun AnalysisSheetInfo.toResponse(taSummary: ScheduleSummary?): AnalysisSheetResponse =
    AnalysisSheetResponse(
        id = id,
        creditCaseId = creditCaseId,
        faVariant = faVariant,
        status = status,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        publishedAt = publishedAt,
        taSummary = taSummary,
    )
