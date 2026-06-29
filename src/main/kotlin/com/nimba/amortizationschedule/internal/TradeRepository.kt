package com.nimba.amortizationschedule.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TradeRepository : JpaRepository<Trade, UUID> {
    fun existsByCreditCaseIdAndActiveIsTrue(creditCaseId: UUID): Boolean

    fun findByCreditCaseIdAndActiveIsTrueOrderByDueDateAsc(creditCaseId: UUID): List<Trade>

    fun findByCreditCaseIdAndActiveIsTrue(creditCaseId: UUID): List<Trade>
}
