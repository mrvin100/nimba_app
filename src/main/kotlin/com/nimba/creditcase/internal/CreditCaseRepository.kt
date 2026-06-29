package com.nimba.creditcase.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CreditCaseRepository : JpaRepository<CreditCase, UUID> {
    fun findByCaseNumber(caseNumber: String): CreditCase?
}
