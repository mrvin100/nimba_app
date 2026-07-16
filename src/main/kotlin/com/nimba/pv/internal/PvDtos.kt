package com.nimba.pv.internal

import com.nimba.pv.CreatePvCommand
import com.nimba.pv.PvDebat
import com.nimba.pv.PvInfo
import com.nimba.pv.PvSnapshot
import com.nimba.pv.PvStatus
import com.nimba.pv.UpdatePvDraftCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreatePvRequest(
    @field:NotNull
    val seanceDate: LocalDate,
)

internal fun CreatePvRequest.toCommand(
    creditCaseId: UUID,
    createdBy: UUID,
): CreatePvCommand = CreatePvCommand(creditCaseId, createdBy, seanceDate)

data class PvDebatRequest(
    @field:NotBlank
    @field:Size(max = 2000, message = "2000 caractères maximum")
    val preoccupation: String,
    @field:NotBlank
    @field:Size(max = 2000, message = "2000 caractères maximum")
    val reponse: String,
    @field:NotBlank
    @field:Size(max = 2000, message = "2000 caractères maximum")
    val recommandation: String,
)

/**
 * Points forts/faibles are deliberately absent: they are read from the FA at
 * finalization (see [PvSnapshot]), never typed on the PV draft.
 */
data class UpdatePvDraftRequest(
    @field:NotNull
    val seanceDate: LocalDate,
    @field:Size(max = 200, message = "200 caractères maximum")
    val rapporteur: String?,
    @field:Size(max = 200, message = "200 caractères maximum")
    val president: String?,
    @field:Valid
    val debats: List<PvDebatRequest> = emptyList(),
)

internal fun UpdatePvDraftRequest.toCommand(): UpdatePvDraftCommand =
    UpdatePvDraftCommand(
        seanceDate = seanceDate,
        rapporteur = rapporteur,
        president = president,
        debats = debats.map { PvDebat(it.preoccupation, it.reponse, it.recommandation) },
    )

data class PvResponse(
    val id: UUID,
    val creditCaseId: UUID,
    val status: PvStatus,
    val seanceDate: LocalDate,
    val rapporteur: String?,
    val president: String?,
    val debats: List<PvDebat>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finalizedAt: Instant?,
    val snapshot: PvSnapshot?,
)

internal fun PvInfo.toResponse(): PvResponse =
    PvResponse(
        id = id,
        creditCaseId = creditCaseId,
        status = status,
        seanceDate = seanceDate,
        rapporteur = rapporteur,
        president = president,
        debats = debats,
        createdAt = createdAt,
        updatedAt = updatedAt,
        finalizedAt = finalizedAt,
        snapshot = snapshot,
    )
