package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionDocumentVersionInfo
import com.nimba.caution.CautionDossierEventInfo
import com.nimba.caution.CautionDossierInfo
import com.nimba.caution.CautionFieldDefinition
import com.nimba.caution.CautionFieldRegistry
import com.nimba.caution.CautionFieldType
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionStatus
import com.nimba.caution.CreateCautionCommand
import com.nimba.caution.CreateDossierCommand
import com.nimba.caution.DossierAction
import com.nimba.caution.DossierStatus
import com.nimba.caution.UpdateCautionCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.util.UUID

data class CreateCautionRequest(
    @field:NotNull
    val clientId: UUID,
    @field:NotNull
    val documentType: CautionDocumentType,
    val content: Map<String, String> = emptyMap(),
    /** Only takes effect for the very first caution ever created — see `CautionNumberGenerator`'s KDoc. */
    @field:Positive
    val startingReferenceSequence: Int? = null,
    /** The dossier this document belongs to, or null when created standalone. */
    val dossierId: UUID? = null,
)

internal fun CreateCautionRequest.toCommand(createdBy: UUID): CreateCautionCommand =
    CreateCautionCommand(
        clientId = clientId,
        documentType = documentType,
        content = content,
        createdBy = createdBy,
        startingReferenceSequence = startingReferenceSequence,
        dossierId = dossierId,
    )

data class UpdateCautionRequest(
    val content: Map<String, String> = emptyMap(),
    /** Journaled in the document's history (used notably for a change made during a prorogation). */
    val reason: String? = null,
)

internal fun UpdateCautionRequest.toCommand(): UpdateCautionCommand = UpdateCautionCommand(content, reason)

data class DocumentVersionResponse(
    val id: UUID,
    val contentBefore: Map<String, String>,
    val contentAfter: Map<String, String>,
    val reason: String?,
    val actor: UUID,
    val createdAt: Instant,
)

internal fun CautionDocumentVersionInfo.toResponse(): DocumentVersionResponse =
    DocumentVersionResponse(
        id = id,
        contentBefore = contentBefore,
        contentAfter = contentAfter,
        reason = reason,
        actor = actor,
        createdAt = createdAt,
    )

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

/**
 * Row of the Cautions data table — the client's name and the creator's name are
 * resolved once per page by the controller (batched, not one request per row)
 * since [CautionInfo] itself only carries their ids.
 */
data class CautionSummaryResponse(
    val id: UUID,
    val clientId: UUID,
    val clientMatricule: String,
    val clientRaisonSociale: String,
    val documentType: CautionDocumentType,
    val referenceNumber: String,
    val status: CautionStatus,
    val createdByName: String,
    val createdAt: Instant,
    val updatedAt: Instant,
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
    val optional: Boolean,
)

internal fun CautionFieldDefinition.toResponse(): CautionFieldDefinitionResponse =
    CautionFieldDefinitionResponse(key = key, label = label, type = type, optional = optional)

internal fun documentTypeResponses(): List<CautionDocumentTypeResponse> =
    CautionDocumentType.entries.map { type ->
        CautionDocumentTypeResponse(
            code = type,
            label = type.label,
            sharedFields = CautionFieldRegistry.SHARED_FIELDS.map { it.toResponse() },
            specificFields = CautionFieldRegistry.specificFieldsFor(type).map { it.toResponse() },
        )
    }

data class ReferenceSequenceStatusResponse(
    val initialized: Boolean,
)

data class CreateDossierRequest(
    @field:NotNull
    val clientId: UUID,
    val content: Map<String, String> = emptyMap(),
    @field:Positive
    val startingReferenceSequence: Int? = null,
)

internal fun CreateDossierRequest.toCommand(createdBy: UUID): CreateDossierCommand =
    CreateDossierCommand(
        clientId = clientId,
        content = content,
        createdBy = createdBy,
        startingReferenceSequence = startingReferenceSequence,
    )

data class UpdateDossierRequest(
    val content: Map<String, String> = emptyMap(),
)

data class ProrogeDossierRequest(
    @field:NotBlank
    val reason: String = "",
)

data class DossierEventResponse(
    val id: UUID,
    val action: DossierAction,
    val fromStatus: DossierStatus,
    val toStatus: DossierStatus,
    val reason: String?,
    val actor: UUID,
    val createdAt: Instant,
)

internal fun CautionDossierEventInfo.toResponse(): DossierEventResponse =
    DossierEventResponse(
        id = id,
        action = action,
        fromStatus = fromStatus,
        toStatus = toStatus,
        reason = reason,
        actor = actor,
        createdAt = createdAt,
    )

data class DossierResponse(
    val id: UUID,
    val clientId: UUID,
    val referenceNumber: String,
    val status: DossierStatus,
    val version: Int,
    val content: Map<String, String>,
    val createdAt: Instant,
    val updatedAt: Instant,
)

internal fun CautionDossierInfo.toResponse(): DossierResponse =
    DossierResponse(
        id = id,
        clientId = clientId,
        referenceNumber = referenceNumber,
        status = status,
        version = version,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

/** A dossier together with the documents attached to it, for the dossier detail view. */
data class DossierDetailResponse(
    val dossier: DossierResponse,
    val documents: List<CautionResponse>,
)
