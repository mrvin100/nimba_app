package com.nimba.creditcase

import java.time.Instant
import java.util.UUID

/**
 * Read-only view of a credit case, safe to share across module boundaries (the
 * amortization-schedule module attaches schedules and trades to a case found
 * through this). Never exposes the internal entity.
 */
data class CreditCaseInfo(
    val id: UUID,
    val caseNumber: String,
    val clientName: String,
    val productType: ProductType,
    val currency: String,
    val status: CreditCaseStatus,
    val createdBy: UUID,
    val createdAt: Instant,
    /** The client's account number at the bank; null until captured on the case. */
    val accountNumber: String? = null,
    /** When an administrator archived the case; null while it is active. */
    val archivedAt: Instant? = null,
)
