package com.nimba.workflow.internal

import com.nimba.workflow.WorkflowModuleApi
import com.nimba.workflow.WorkflowStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkflowModuleApiService(
    private val workflows: CaseWorkflowRepository,
) : WorkflowModuleApi {
    @Transactional(readOnly = true)
    override fun statusOf(creditCaseId: UUID): WorkflowStatus? = workflows.findByCreditCaseId(creditCaseId)?.status
}
