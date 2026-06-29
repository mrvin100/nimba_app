package com.nimba.creditcase

/**
 * Simple phase-1 status of a credit case, reflecting only whether trades have been
 * generated from an amortization schedule yet. This is NOT the full
 * cross-directorate workflow status (DRI → DCM → Risques → Comité), which belongs
 * to a later phase.
 */
enum class CreditCaseStatus {
    EN_ATTENTE_AMORTISSEMENT,
    TRADES_GENERES,
}
