package com.nimba.creditcase

/**
 * Request to update a credit case's general information. The case number and creator
 * are immutable, so only the client-facing fields can change here.
 */
data class UpdateCreditCaseCommand(
    val clientName: String,
    val productType: ProductType,
    val currency: String,
    /** The client's account number at the bank (printed on the traités). */
    val accountNumber: String? = null,
    /** Required when [productType] is LEASING; must be null otherwise. */
    val contractType: ContractType? = null,
)
