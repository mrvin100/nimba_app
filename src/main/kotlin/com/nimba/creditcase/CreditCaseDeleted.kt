package com.nimba.creditcase

import java.util.UUID

/**
 * Published when an administrator definitively deletes a credit case, inside the
 * deleting transaction. Modules that attach data to a case by id (the
 * amortization-schedule module's schedules, trades and retained CSV files) listen
 * and purge their side, so the deletion stays atomic without the creditcase module
 * ever knowing who depends on it — a dependency in that direction would be a cycle.
 */
data class CreditCaseDeleted(
    val creditCaseId: UUID,
)
