package com.nimba.creditcase.internal

import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import com.nimba.creditcase.UpdateClientIdentityCommand
import com.nimba.creditcase.UpdateConditionsDeBanqueCommand
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Write payload for a credit case, shared by create and update — both carry the
 * same editable fields with the same validation. The immutable fields
 * (caseNumber, createdBy) are never client-supplied: the service generates or
 * stamps them on create and refuses to touch them on update.
 */
data class CreditCaseWriteRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 200, message = "Le nom du client doit faire entre 1 et 200 caractères")
    val clientName: String,
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
 * Write payload for a case's client-identity details — a separate concern from
 * [CreditCaseWriteRequest] (identity is optional supplementary detail added
 * incrementally, not part of the required create/edit fields). Every field is
 * optional; blanks are treated as "not captured".
 */
data class ClientIdentityRequest(
    @field:Size(max = 100, message = "100 caractères maximum")
    val formeJuridique: String? = null,
    val dateCreation: LocalDate? = null,
    @field:Size(max = 300, message = "300 caractères maximum")
    val adressePhysique: String? = null,
    @field:Size(max = 300, message = "300 caractères maximum")
    val activiteDeBase: String? = null,
    @field:Size(max = 50, message = "50 caractères maximum")
    val codeNif: String? = null,
    @field:Size(max = 200, message = "200 caractères maximum")
    val principalDirigeant: String? = null,
    val dateEntreeRelation: LocalDate? = null,
    val dateDerniereVisite: LocalDate? = null,
    @field:Size(max = 100, message = "100 caractères maximum")
    val agence: String? = null,
    @field:Size(max = 200, message = "200 caractères maximum")
    val gestionnaire: String? = null,
    @field:Size(max = 200, message = "200 caractères maximum")
    val analyste: String? = null,
    @field:Size(max = 20, message = "20 caractères maximum")
    val cotationPrecedente: String? = null,
    @field:Size(max = 20, message = "20 caractères maximum")
    val cotationActuelle: String? = null,
)

internal fun ClientIdentityRequest.toCommand(): UpdateClientIdentityCommand =
    UpdateClientIdentityCommand(
        formeJuridique = formeJuridique,
        dateCreation = dateCreation,
        adressePhysique = adressePhysique,
        activiteDeBase = activiteDeBase,
        codeNif = codeNif,
        principalDirigeant = principalDirigeant,
        dateEntreeRelation = dateEntreeRelation,
        dateDerniereVisite = dateDerniereVisite,
        agence = agence,
        gestionnaire = gestionnaire,
        analyste = analyste,
        cotationPrecedente = cotationPrecedente,
        cotationActuelle = cotationActuelle,
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
    @field:Size(max = 4000, message = "4000 caractères maximum")
    val fraisDivers: String? = null,
)

internal fun ConditionsDeBanqueRequest.toCommand(): UpdateConditionsDeBanqueCommand =
    UpdateConditionsDeBanqueCommand(
        tauxInteretPct = tauxInteretPct,
        fraisMiseEnPlacePct = fraisMiseEnPlacePct,
        comEngagementPct = comEngagementPct,
        fraisEtudesPct = fraisEtudesPct,
        fraisDivers = fraisDivers,
    )

/** Row in the dashboard's credit-case list. */
data class CreditCaseSummaryResponse(
    val id: UUID,
    val caseNumber: String,
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
        clientName = clientName,
        productType = productType,
        contractType = contractType,
        status = status,
        createdAt = createdAt,
        archivedAt = archivedAt,
    )
