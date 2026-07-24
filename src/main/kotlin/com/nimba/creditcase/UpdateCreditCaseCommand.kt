package com.nimba.creditcase

import java.util.UUID

/**
 * Request to update a credit case's general information. The case number and creator
 * are immutable; the dossier may be reassigned to a different [clientId].
 */
data class UpdateCreditCaseCommand(
    val clientId: UUID,
    val productType: ProductType,
    val currency: String,
    /** The client's account number at the bank (printed on the traités). */
    val accountNumber: String? = null,
    /** Required when [productType] is LEASING; must be null otherwise. */
    val contractType: ContractType? = null,
)
