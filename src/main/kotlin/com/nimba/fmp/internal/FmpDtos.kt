package com.nimba.fmp.internal

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.fmp.CreateFmpCommand
import com.nimba.fmp.FmpInfo
import com.nimba.pv.PvGuaranteeSnapshot
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateFmpRequest(
    @field:NotBlank
    @field:Size(max = 50, message = "50 caractères maximum")
    val numeroPret: String,
    @field:Size(max = 100, message = "100 caractères maximum")
    val garantieRef: String?,
)

internal fun CreateFmpRequest.toCommand(
    creditCaseId: UUID,
    createdBy: UUID,
): CreateFmpCommand = CreateFmpCommand(creditCaseId, createdBy, numeroPret, garantieRef)

data class FmpResponse(
    val id: UUID,
    val creditCaseId: UUID,
    val numeroPret: String,
    val garantieRef: String?,
    val createdAt: Instant,
    val caseNumber: String,
    val clientName: String,
    val accountNumber: String?,
    val gfcEnCharge: String,
    val identite: ClientIdentityInfo,
    val articulation: ScheduleSummary,
    val garanties: List<PvGuaranteeSnapshot>,
    val conditionsDeBanque: ConditionsDeBanqueInfo,
)

internal fun FmpInfo.toResponse(): FmpResponse =
    FmpResponse(
        id = id,
        creditCaseId = creditCaseId,
        numeroPret = numeroPret,
        garantieRef = garantieRef,
        createdAt = createdAt,
        caseNumber = caseNumber,
        clientName = clientName,
        accountNumber = accountNumber,
        gfcEnCharge = gfcEnCharge,
        identite = identite,
        articulation = articulation,
        garanties = garanties,
        conditionsDeBanque = conditionsDeBanque,
    )
