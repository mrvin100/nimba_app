package com.nimba.creditcase.internal

import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import com.nimba.creditcase.UpdateConditionsDeBanqueCommand
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Write payload for a credit case, shared by create and update — both carry the
 * same editable fields with the same validation. The immutable fields
 * (caseNumber, createdBy) are never client-supplied: the service generates or
 * stamps them on create and refuses to touch them on update.
 */
data class CreditCaseWriteRequest(
    @field:NotNull(message = "Le client est requis")
    val clientId: UUID,
    @field:NotNull
    val productType: ProductType,
    /** Required when [productType] is LEASING; must be omitted otherwise (service-validated). */
    val contractType: ContractType? = null,
    @field:NotBlank
    @field:Pattern(regexp = "[A-Z]{3}", message = "La devise doit être un code à 3 lettres majuscules (ex. GNF)")
    val currency: String,
    @field:Size(max = 50, message = "Le numéro de compte ne peut dépasser 50 caractères")
    val accountNumber: String? = null,
)

data class CreditCaseResponse(
    val id: UUID,
    val caseNumber: String,
    val clientId: UUID,
    val clientName: String,
    val productType: ProductType,
    val contractType: ContractType?,
    val currency: String,
    val status: CreditCaseStatus,
    val createdAt: Instant,
    val accountNumber: String?,
    val archivedAt: Instant?,
    val clientIdentity: ClientIdentityInfo,
    val conditionsDeBanque: ConditionsDeBanqueInfo,
)

internal fun CreditCaseInfo.toResponse(): CreditCaseResponse =
    CreditCaseResponse(
        id = id,
        caseNumber = caseNumber,
        clientId = clientId,
        clientName = clientName,
        productType = productType,
        contractType = contractType,
        currency = currency,
        status = status,
        createdAt = createdAt,
        accountNumber = accountNumber,
        archivedAt = archivedAt,
        clientIdentity = clientIdentity,
        conditionsDeBanque = conditionsDeBanque,
    )

/**
 * Write payload for a case's conditions-de-banque details — separate from
 * [CreditCaseWriteRequest] for the same reason as [ClientIdentityRequest]: optional
 * supplementary detail the DRI adds incrementally. Percentages are stored as
 * plain numbers (e.g. `2.5` for 2.5%), not fractions. [fraisDivers] is opaque
 * JSON text the frontend serializes; this layer only bounds its size.
 */
data class ConditionsDeBanqueRequest(
    @field:Digits(integer = 3, fraction = 3, message = "Pourcentage invalide")
    val tauxInteretPct: BigDecimal? = null,
    @field:Digits(integer = 3, fraction = 3, message = "Pourcentage invalide")
    val fraisMiseEnPlacePct: BigDecimal? = null,
    @field:Digits(integer = 3, fraction = 3, message = "Pourcentage invalide")
    val comEngagementPct: BigDecimal? = null,
    @field:Digits(integer = 3, fraction = 3, message = "Pourcentage invalide")
    val fraisEtudesPct: BigDecimal? = null,
    @field:Digits(integer = 3, fraction = 3, message = "Pourcentage invalide")
    val valeurResiduellePct: BigDecimal? = null,
    @field:Size(max = 4000, message = "4000 caractères maximum")
    val fraisDivers: String? = null,
)

internal fun ConditionsDeBanqueRequest.toCommand(): UpdateConditionsDeBanqueCommand =
    UpdateConditionsDeBanqueCommand(
        tauxInteretPct = tauxInteretPct,
        fraisMiseEnPlacePct = fraisMiseEnPlacePct,
        comEngagementPct = comEngagementPct,
        fraisEtudesPct = fraisEtudesPct,
        valeurResiduellePct = valeurResiduellePct,
        fraisDivers = fraisDivers,
    )

/** Row in the dashboard's credit-case list. */
data class CreditCaseSummaryResponse(
    val id: UUID,
    val caseNumber: String,
    val clientId: UUID,
    val clientName: String,
    val productType: ProductType,
    val contractType: ContractType?,
    val status: CreditCaseStatus,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

internal fun CreditCaseInfo.toSummaryResponse(): CreditCaseSummaryResponse =
    CreditCaseSummaryResponse(
        id = id,
        caseNumber = caseNumber,
        clientId = clientId,
        clientName = clientName,
        productType = productType,
        contractType = contractType,
        status = status,
        createdAt = createdAt,
        archivedAt = archivedAt,
    )
