package com.nimba.workflow.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WorkflowEventRepository : JpaRepository<WorkflowEvent, UUID> {
    /** The dossier's timeline, oldest first. */
    fun findByCreditCaseIdOrderByOccurredAtAsc(creditCaseId: UUID): List<WorkflowEvent>

    fun deleteByCreditCaseId(creditCaseId: UUID)
}
