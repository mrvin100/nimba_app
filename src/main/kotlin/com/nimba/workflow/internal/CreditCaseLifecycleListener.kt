package com.nimba.workflow.internal

import com.nimba.creditcase.CreditCaseCreated
import com.nimba.creditcase.CreditCaseDeleted
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Keeps a dossier's workflow row in step with its credit case, reacting to the
 * creditcase module's lifecycle events rather than depending on the workflow module
 * from there (which would be a cycle). Both run synchronously inside the publishing
 * transaction, so the workflow row appears and disappears atomically with the case.
 */
@Component
class CreditCaseLifecycleListener(
    private val workflow: WorkflowService,
) {
    @EventListener
    fun onCreated(event: CreditCaseCreated) {
        workflow.initialize(event.creditCaseId)
    }

    @EventListener
    fun onDeleted(event: CreditCaseDeleted) {
        workflow.purge(event.creditCaseId)
    }
}
