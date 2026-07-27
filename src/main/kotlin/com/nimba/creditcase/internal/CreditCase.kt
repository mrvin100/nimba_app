package com.nimba.creditcase.internal

import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
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
    /**
     * The client this dossier is for — the `client` module's aggregate, referenced by
     * id only (no JPA relationship crosses the module boundary). The client is the
     * single source of the dossier's client identity (name, NIF, dirigeant…); the
     * credit-case module no longer stores an embedded copy. Reassignable via update.
     */
    @Column(name = "client_id", nullable = false)
    var clientId: UUID,
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

    /**
     * Bank-set financing terms reused across the FA/PV/FMP; see [ConditionsDeBanque].
     * Nullable because Hibernate reloads an `@Embedded` value object as null (not an
     * empty instance) once every one of its columns is null — true for every case
     * until the DRI captures the first term. Read through [CreditCase.conditionsOrEmpty],
     * never this property directly.
     */
    @Embedded
    var conditionsDeBanque: ConditionsDeBanque? = ConditionsDeBanque()
}

/** [CreditCase.conditionsDeBanque], defaulting the "nothing captured yet" case to an empty value object. */
internal fun CreditCase.conditionsOrEmpty(): ConditionsDeBanque = conditionsDeBanque ?: ConditionsDeBanque()
