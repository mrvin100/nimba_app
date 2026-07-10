package com.nimba.creditcase

/**
 * Which amortization-schedule (TA) shape a case's product expects. LEASING carries
 * loyer/VR columns; CORE_BANKING is the core-banking "tableau d'amortissement
 * validé crédit" printout (MC2/MUFFA) and has neither. The amortization-schedule
 * module reads this to pick the matching parser and consistency rules.
 */
enum class ScheduleFormat {
    LEASING,
    CORE_BANKING,
}
