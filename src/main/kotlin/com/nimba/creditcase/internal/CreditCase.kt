package com.nimba.creditcase.internal

import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A client credit case — the minimal entity an amortization schedule and its
 * trades attach to in this phase. Deliberately a small subset of the full Prodigy
 * vision (analysis sheet, guarantees, etc. belong to later epics); it is shaped to
 * receive those extensions without a destructive migration. [createdBy] is the id
 * of the DRI analyst who opened it (resolved through the identity module's API).
 */
@Entity
@Table(name = "credit_case")
class CreditCase(
    @Column(name = "case_number", nullable = false, unique = true, updatable = false)
    val caseNumber: String,
    @Column(name = "client_name", nullable = false)
    var clientName: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    var productType: ProductType,
    /** Only set when [productType] is LEASING; null for every other product. */
    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type")
    var contractType: ContractType? = null,
    @Column(name = "currency", nullable = false)
    var currency: String,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
    /** The client's account number at the bank (printed on the traités). */
    @Column(name = "account_number")
    var accountNumber: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: CreditCaseStatus = CreditCaseStatus.EN_ATTENTE_AMORTISSEMENT

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    /** When an administrator archived the case; null while it is active. */
    @Column(name = "archived_at")
    var archivedAt: Instant? = null
}
