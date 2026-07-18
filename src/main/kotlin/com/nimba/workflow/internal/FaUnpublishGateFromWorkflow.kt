package com.nimba.workflow.internal

import com.nimba.analysissheet.FaUnpublishGate
import com.nimba.workflow.WorkflowStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The workflow-side implementation of [FaUnpublishGate] (dependency inversion —
 * see that interface's KDoc): the DRI may take the FA back to draft only while
 * the dossier is still BROUILLON and was never submitted to review. Any later
 * reopening happens through a reviewer's return, never a self-unpublish.
 */
@Component
class FaUnpublishGateFromWorkflow(
    private val workflows: CaseWorkflowRepository,
    private val events: WorkflowEventRepository,
) : FaUnpublishGate {
    @Transactional(readOnly = true)
    override fun canUnpublish(creditCaseId: UUID): Boolean {
        val workflow = workflows.findByCreditCaseId(creditCaseId) ?: return true
        if (workflow.status != WorkflowStatus.BROUILLON) return false
        return events.findByCreditCaseIdOrderByOccurredAtAsc(creditCaseId).isEmpty()
    }
}
