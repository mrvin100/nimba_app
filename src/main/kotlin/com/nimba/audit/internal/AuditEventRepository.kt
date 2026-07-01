package com.nimba.audit.internal

import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.time.Instant
import java.util.UUID

interface AuditEventRepository :
    JpaRepository<AuditEvent, UUID>,
    JpaSpecificationExecutor<AuditEvent>

/**
 * A specification applying only the provided (non-null) audit filters — period
 * ([from] inclusive, [to] exclusive), HTTP [method], HTTP [status] code. Built with the
 * Criteria API so an absent filter simply adds no predicate (avoiding the `:param is
 * null` null-typed-parameter pitfall of JPQL). Ordering/pagination come from the caller.
 */
fun auditFilter(
    from: Instant?,
    to: Instant?,
    method: String?,
    status: Int?,
): Specification<AuditEvent> =
    Specification { root, _, cb ->
        val predicates = mutableListOf<Predicate>()
        from?.let { predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), it)) }
        to?.let { predicates.add(cb.lessThan(root.get("occurredAt"), it)) }
        method?.let { predicates.add(cb.equal(root.get<String>("method"), it)) }
        status?.let { predicates.add(cb.equal(root.get<Int>("status"), it)) }
        cb.and(*predicates.toTypedArray())
    }
