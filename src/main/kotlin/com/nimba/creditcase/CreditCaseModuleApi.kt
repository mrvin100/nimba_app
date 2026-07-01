package com.nimba.creditcase

import java.util.UUID

/**
 * The credit-case module's public API. Other modules create and read cases through
 * this interface only — never through the repository or entity, which are internal
 * to the module.
 */
interface CreditCaseModuleApi {
    fun createCase(command: CreateCreditCaseCommand): CreditCaseInfo

    /** Updates a case's general information (client, product, currency); 404 if unknown. */
    fun updateCase(
        id: UUID,
        command: UpdateCreditCaseCommand,
    ): CreditCaseInfo

    fun findById(id: UUID): CreditCaseInfo?

    fun findByCaseNumber(caseNumber: String): CreditCaseInfo?

    /** Flips the case status to TRADES_GENERES once trades have been generated for it. */
    fun markTradesGenerated(creditCaseId: UUID)
}
