package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionFieldDefinition
import com.nimba.caution.CautionFieldRegistry
import com.nimba.caution.CautionFieldType
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionStatus
import com.nimba.caution.CreateCautionCommand
import com.nimba.caution.UpdateCautionCommand
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

data class CreateCautionRequest(
    @field:NotNull
    val clientId: UUID,
    @field:NotNull
    val documentType: CautionDocumentType,
    val content: Map<String, String> = emptyMap(),
)

internal fun CreateCautionRequest.toCommand(createdBy: UUID): CreateCautionCommand =
    CreateCautionCommand(clientId = clientId, documentType = documentType, content = content, createdBy = createdBy)

data class UpdateCautionRequest(
    val content: Map<String, String> = emptyMap(),
)

internal fun UpdateCautionRequest.toCommand(): UpdateCautionCommand = UpdateCautionCommand(content)

data class CautionResponse(
    val id: UUID,
    val clientId: UUID,
    val documentType: CautionDocumentType,
    val referenceNumber: String,
    val status: CautionStatus,
    val content: Map<String, String>,
    val clientSnapshot: CautionClientSnapshotInfo?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finalizedAt: Instant?,
)

internal fun CautionInfo.toResponse(): CautionResponse =
    CautionResponse(
        id = id,
        clientId = clientId,
        documentType = documentType,
        referenceNumber = referenceNumber,
        status = status,
        content = content,
        clientSnapshot = clientSnapshot,
        createdAt = createdAt,
        updatedAt = updatedAt,
        finalizedAt = finalizedAt,
    )

/** One document-type metadata entry — the frontend's dynamic form is built from this, never hardcoded per type. */
data class CautionDocumentTypeResponse(
    val code: CautionDocumentType,
    val label: String,
    val sharedFields: List<CautionFieldDefinitionResponse>,
    val specificFields: List<CautionFieldDefinitionResponse>,
)

data class CautionFieldDefinitionResponse(
    val key: String,
    val label: String,
    val type: CautionFieldType,
)

internal fun CautionFieldDefinition.toResponse(): CautionFieldDefinitionResponse =
    CautionFieldDefinitionResponse(key = key, label = label, type = type)

internal fun documentTypeResponses(): List<CautionDocumentTypeResponse> =
    CautionDocumentType.entries.map { type ->
        CautionDocumentTypeResponse(
            code = type,
            label = type.label,
            sharedFields = CautionFieldRegistry.SHARED_FIELDS.map { it.toResponse() },
            specificFields = CautionFieldRegistry.specificFieldsFor(type).map { it.toResponse() },
        )
    }
