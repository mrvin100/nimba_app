package com.nimba.amortizationschedule

import java.util.UUID

/**
 * The amortization-schedule module's public API. Other modules read the module's
 * state through this interface only — never through its repositories or entities.
 * Minimal for this phase: whether a case currently has an active set of generated
 * trades.
 */
interface AmortizationScheduleModuleApi {
    fun hasActiveTradesForCase(creditCaseId: UUID): Boolean
}
