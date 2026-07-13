package com.nimba.fmp.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FmpRepository : JpaRepository<Fmp, UUID> {
    fun findByCreditCaseId(creditCaseId: UUID): Fmp?

    fun existsByCreditCaseId(creditCaseId: UUID): Boolean

    fun deleteByCreditCaseId(creditCaseId: UUID)
}
