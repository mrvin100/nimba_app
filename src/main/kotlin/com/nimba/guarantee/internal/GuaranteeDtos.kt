package com.nimba.guarantee.internal

import com.nimba.guarantee.GuaranteeAttachmentInfo
import com.nimba.guarantee.GuaranteeInfo
import com.nimba.guarantee.GuaranteeKind
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class GuaranteeWriteRequest(
    @field:NotNull
    val kind: GuaranteeKind,
    @field:NotBlank
    @field:Size(max = 1000, message = "1000 caractères maximum")
    val description: String,
)

data class GuaranteeAttachmentResponse(
    val id: UUID,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val uploadedAt: Instant,
)

data class GuaranteeResponse(
    val id: UUID,
    val creditCaseId: UUID,
    val kind: GuaranteeKind,
    val description: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val attachments: List<GuaranteeAttachmentResponse>,
)

internal fun GuaranteeInfo.toResponse(): GuaranteeResponse =
    GuaranteeResponse(
        id = id,
        creditCaseId = creditCaseId,
        kind = kind,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        attachments = attachments.map { it.toResponse() },
    )

private fun GuaranteeAttachmentInfo.toResponse(): GuaranteeAttachmentResponse =
    GuaranteeAttachmentResponse(
        id = id,
        fileName = fileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        uploadedAt = uploadedAt,
    )
