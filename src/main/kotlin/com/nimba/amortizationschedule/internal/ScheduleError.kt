package com.nimba.amortizationschedule.internal

/**
 * A single problem detected while reading or validating an uploaded schedule,
 * described in terms an analyst (not a developer) can act on. [lineNumber] is the
 * 1-based line in the file (the header is line 1); null for a whole-file problem.
 * Parser errors (NIMBA-15) and consistency errors (NIMBA-16) share this shape so
 * the analyst sees a single unified list.
 */
data class ScheduleError(
    val lineNumber: Long?,
    val column: String?,
    val message: String,
)
