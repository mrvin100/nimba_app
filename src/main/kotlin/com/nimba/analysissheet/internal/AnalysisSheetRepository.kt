package com.nimba.analysissheet.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnalysisSheetRepository : JpaRepository<AnalysisSheet, UUID> {
    fun findByCreditCaseId(creditCaseId: UUID): AnalysisSheet?

    fun existsByCreditCaseId(creditCaseId: UUID): Boolean

    fun deleteByCreditCaseId(creditCaseId: UUID)
}
