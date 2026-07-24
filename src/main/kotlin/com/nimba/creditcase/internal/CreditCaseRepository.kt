package com.nimba.creditcase.internal

import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface CreditCaseRepository :
    JpaRepository<CreditCase, UUID>,
    JpaSpecificationExecutor<CreditCase> {
    fun findByCaseNumber(caseNumber: String): CreditCase?

    /** Count of credit cases in a given phase-1 status. */
    fun countByStatus(status: CreditCaseStatus): Long
}

/**
 * Builds the dashboard/registre list filter. Every criterion is optional and only
 * adds a predicate when set (a JPQL `:x is null` guard would fail on null-typed
 * params — see the audit module's same Specification approach). [archived] narrows
 * to archived (true) or active (false) cases; null returns both.
 */
internal fun creditCaseFilter(
    archived: Boolean?,
    clientId: UUID?,
    productType: ProductType?,
): Specification<CreditCase> =
    Specification { root, _, cb ->
        val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
        when (archived) {
            true -> predicates += cb.isNotNull(root.get<Any>("archivedAt"))
            false -> predicates += cb.isNull(root.get<Any>("archivedAt"))
            null -> {}
        }
        if (clientId != null) predicates += cb.equal(root.get<UUID>("clientId"), clientId)
        if (productType != null) predicates += cb.equal(root.get<ProductType>("productType"), productType)
        cb.and(*predicates.toTypedArray())
    }
