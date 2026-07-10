package com.nimba.creditcase.internal

import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
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
