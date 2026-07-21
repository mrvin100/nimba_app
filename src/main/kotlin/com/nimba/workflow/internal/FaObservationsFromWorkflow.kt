package com.nimba.workflow.internal

import com.nimba.analysissheet.FaObservation
import com.nimba.analysissheet.FaObservationsProvider
import com.nimba.workflow.WorkflowAction
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The workflow-side implementation of the analysissheet module's
 * [FaObservationsProvider] (dependency inversion — see that interface's KDoc):
 * each non-blank line of a REQUEST_COMPLETION comment is one observation,
 * resolved once a SUBMIT followed it in the timeline.
 */
@Component
class FaObservationsFromWorkflow(
    private val events: WorkflowEventRepository,
) : FaObservationsProvider {
    @Transactional(readOnly = true)
    override fun observationsFor(creditCaseId: UUID): List<FaObservation> {
        val timeline = events.findByCreditCaseIdOrderByOccurredAtAsc(creditCaseId)
        return timeline
            .filter { it.action == WorkflowAction.REQUEST_COMPLETION && !it.comment.isNullOrBlank() }
            .flatMap { event ->
                // Resolved once the DRI has resubmitted after the observation —
                // through the corrections lane (design §12.1) or a full resubmit.
                val resolved =
                    timeline.any {
                        (it.action == WorkflowAction.SUBMIT || it.action == WorkflowAction.SUBMIT_CORRECTIONS) &&
                            it.occurredAt.isAfter(event.occurredAt)
                    }
                event.comment
                    .orEmpty()
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { FaObservation(it, resolved) }
            }
    }
}
