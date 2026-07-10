package com.nimba.creditcase

import java.util.UUID

/**
 * Published when a credit case is opened, inside the creating transaction. The
 * workflow module listens and initialises the dossier's lifecycle at BROUILLON, so
 * the creditcase module never has to know a workflow exists — a dependency in that
 * direction would be a cycle.
 */
data class CreditCaseCreated(
    val creditCaseId: UUID,
)
