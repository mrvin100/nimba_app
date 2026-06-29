package com.nimba.amortizationschedule.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AmortizationScheduleRepository : JpaRepository<AmortizationSchedule, UUID> {
    /** The most recently uploaded schedule version for a case, or null if none. */
    fun findFirstByCreditCaseIdOrderByVersionNumberDesc(creditCaseId: UUID): AmortizationSchedule?
}
