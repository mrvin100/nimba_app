package com.nimba.creditcase.internal

import com.nimba.creditcase.CreditCaseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CreditCaseRepository : JpaRepository<CreditCase, UUID> {
    fun findByCaseNumber(caseNumber: String): CreditCase?

    /** Count of credit cases in a given phase-1 status. */
    fun countByStatus(status: CreditCaseStatus): Long

    /** Active cases only (archived_at doubles as the flag: NULL = active). */
    fun findByArchivedAtIsNull(pageable: Pageable): Page<CreditCase>

    /** Archived cases only. */
    fun findByArchivedAtIsNotNull(pageable: Pageable): Page<CreditCase>
}
