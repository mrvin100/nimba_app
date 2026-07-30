package com.nimba.creditcase

import java.util.UUID

/**
 * Request to open a new credit case. [clientId] references an existing client in the
 * `client` module (the single source of client identity); the module verifies it
 * resolves before persisting. [createdBy] is the id of the authenticated DRI analyst
 * triggering the creation, likewise verified through the identity module.
 */
data class CreateCreditCaseCommand(
    val clientId: UUID,
    val productType: ProductType,
    val currency: String,
    val createdBy: UUID,
    /** The client's account number at the bank (printed on the traités). */
    val accountNumber: String? = null,
    /** Required when [productType] is LEASING; must be null otherwise. */
    val contractType: ContractType? = null,
)
