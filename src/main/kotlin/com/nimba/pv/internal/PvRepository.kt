package com.nimba.pv.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PvRepository : JpaRepository<Pv, UUID> {
    fun findByCreditCaseId(creditCaseId: UUID): Pv?

    fun existsByCreditCaseId(creditCaseId: UUID): Boolean

    fun deleteByCreditCaseId(creditCaseId: UUID)
}
