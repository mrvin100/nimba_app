package com.nimba.amortizationschedule

import java.util.UUID

/**
 * The amortization-schedule module's public API. Other modules read the module's
 * state through this interface only — never through its repositories or entities.
 */
interface AmortizationScheduleModuleApi {
    fun hasActiveTradesForCase(creditCaseId: UUID): Boolean

    /** Whether a case has at least one uploaded amortization schedule (any version). */
    fun hasScheduleForCase(creditCaseId: UUID): Boolean

    /** TA figures for the latest schedule version, or null if none was uploaded. */
    fun scheduleSummary(creditCaseId: UUID): ScheduleSummary?
}
