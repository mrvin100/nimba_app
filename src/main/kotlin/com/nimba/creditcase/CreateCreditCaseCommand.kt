package com.nimba.creditcase

import java.util.UUID

/**
 * Request to open a new credit case. [createdBy] is the id of the authenticated
 * DRI analyst triggering the creation; the module verifies it resolves to a real
 * user through the identity module before persisting.
 */
data class CreateCreditCaseCommand(
    val clientName: String,
    val productType: ProductType,
    val currency: String,
    val createdBy: UUID,
    /** The client's account number at the bank (printed on the traités). */
    val accountNumber: String? = null,
    /** Required when [productType] is LEASING; must be null otherwise. */
    val contractType: ContractType? = null,
)
