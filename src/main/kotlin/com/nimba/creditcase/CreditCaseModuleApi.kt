package com.nimba.creditcase

import java.util.UUID

/**
 * The credit-case module's public API. Other modules create and read cases through
 * this interface only — never through the repository or entity, which are internal
 * to the module.
 */
interface CreditCaseModuleApi {
    fun createCase(command: CreateCreditCaseCommand): CreditCaseInfo

    fun findById(id: UUID): CreditCaseInfo?

    fun findByCaseNumber(caseNumber: String): CreditCaseInfo?

    /** Flips the case status to TRADES_GENERES once trades have been generated for it. */
    fun markTradesGenerated(creditCaseId: UUID)
}
