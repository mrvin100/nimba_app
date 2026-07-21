package com.nimba.workflow.internal

import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.WorkflowModuleApi
import com.nimba.workflow.WorkflowStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WorkflowModuleApiService(
    private val workflows: CaseWorkflowRepository,
    private val workflowService: WorkflowService,
) : WorkflowModuleApi {
    @Transactional(readOnly = true)
    override fun statusOf(creditCaseId: UUID): WorkflowStatus? = workflows.findByCreditCaseId(creditCaseId)?.status

    @Transactional
    override fun act(
        creditCaseId: UUID,
        actorId: UUID,
        action: WorkflowAction,
        comment: String?,
    ): WorkflowStatus = workflowService.act(creditCaseId, actorId, action, comment).status
}
