package com.nimba.creditcase.internal

import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class CreateCreditCaseRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 200, message = "Le nom du client doit faire entre 1 et 200 caractères")
    val clientName: String,
    @field:NotNull
    val productType: ProductType,
    @field:NotBlank
    @field:Pattern(regexp = "[A-Z]{3}", message = "La devise doit être un code à 3 lettres majuscules (ex. GNF)")
    val currency: String,
)

data class UpdateCreditCaseRequest(
    @field:NotBlank
    @field:Size(min = 1, max = 200, message = "Le nom du client doit faire entre 1 et 200 caractères")
    val clientName: String,
    @field:NotNull
    val productType: ProductType,
    @field:NotBlank
    @field:Pattern(regexp = "[A-Z]{3}", message = "La devise doit être un code à 3 lettres majuscules (ex. GNF)")
    val currency: String,
)

data class CreditCaseResponse(
    val id: UUID,
    val caseNumber: String,
    val clientName: String,
    val productType: ProductType,
    val currency: String,
    val status: CreditCaseStatus,
    val createdAt: Instant,
)

internal fun CreditCaseInfo.toResponse(): CreditCaseResponse =
    CreditCaseResponse(
        id = id,
        caseNumber = caseNumber,
        clientName = clientName,
        productType = productType,
        currency = currency,
        status = status,
        createdAt = createdAt,
    )

/** Row in the dashboard's credit-case list. */
data class CreditCaseSummaryResponse(
    val id: UUID,
    val caseNumber: String,
    val clientName: String,
    val productType: ProductType,
    val status: CreditCaseStatus,
    val createdAt: Instant,
)

internal fun CreditCase.toSummaryResponse(): CreditCaseSummaryResponse =
    CreditCaseSummaryResponse(
        id = requireNotNull(id),
        caseNumber = caseNumber,
        clientName = clientName,
        productType = productType,
        status = status,
        createdAt = createdAt,
    )

/**
 * Stable pagination envelope for list endpoints. Defined explicitly rather than
 * serializing Spring Data's `Page`, whose JSON shape is not a guaranteed contract.
 */
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)
