package com.nimba.analysissheet.internal

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.analysissheet.AnalysisSheetInfo
import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.analysissheet.FaPilier
import com.nimba.analysissheet.FaSectionImageInfo
import com.nimba.analysissheet.FaSectionInfo
import com.nimba.analysissheet.FaSectionKey
import com.nimba.analysissheet.FaSectionType
import com.nimba.creditcase.FaVariant
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class AnalysisSheetResponse(
    val id: UUID,
    val creditCaseId: UUID,
    val faVariant: FaVariant,
    val status: AnalysisSheetStatus,
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
        createdAt = createdAt,
        updatedAt = updatedAt,
        publishedAt = publishedAt,
        taSummary = taSummary,
    )

data class FaSectionRequest(
    @field:Size(max = 20000, message = "Le contenu ne peut dépasser 20 000 caractères")
    val contentJson: String?,
)

data class FaSectionResponse(
    val key: FaSectionKey,
    val pilier: FaPilier,
    val type: FaSectionType,
    val label: String,
    val contentJson: String?,
    val updatedAt: Instant?,
    /** Prefill for sections that start populated (e.g. §4.1's risk matrix) — the editor seeds from it. */
    val defaultContentJson: String?,
    /** Uploaded figures, IMAGE-type sections only. */
    val images: List<FaSectionImageResponse>,
)

internal fun FaSectionInfo.toResponse(): FaSectionResponse =
    FaSectionResponse(
        key = key,
        pilier = pilier,
        type = type,
        label = label,
        contentJson = contentJson,
        updatedAt = updatedAt,
        defaultContentJson = defaultContentJson,
        images = images.map { it.toResponse() },
    )

data class FaSectionImageResponse(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val caption: String?,
    val uploadedAt: Instant,
)

internal fun FaSectionImageInfo.toResponse(): FaSectionImageResponse =
    FaSectionImageResponse(
        id = id,
        fileName = fileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        caption = caption,
        uploadedAt = uploadedAt,
    )

data class FaSectionImageCaptionRequest(
    @field:Size(max = 300, message = "La légende ne peut dépasser 300 caractères")
    val caption: String?,
)
