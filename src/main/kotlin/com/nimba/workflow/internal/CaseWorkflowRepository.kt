package com.nimba.workflow.internal

import com.nimba.workflow.WorkflowStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CaseWorkflowRepository : JpaRepository<CaseWorkflow, UUID> {
    fun findByCreditCaseId(creditCaseId: UUID): CaseWorkflow?

    fun existsByCreditCaseId(creditCaseId: UUID): Boolean

    fun findByStatus(status: WorkflowStatus): List<CaseWorkflow>

    fun deleteByCreditCaseId(creditCaseId: UUID)
}
