package com.nimba.guarantee.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface GuaranteeRepository : JpaRepository<Guarantee, UUID> {
    /** Every guarantee of a case, oldest first; also used by the purge listener to resolve storage keys. */
    fun findByCreditCaseIdOrderByCreatedAtAsc(creditCaseId: UUID): List<Guarantee>

    fun deleteByCreditCaseId(creditCaseId: UUID)
}
