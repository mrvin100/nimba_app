package com.nimba.workflow.internal

import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The dossier workflow surface. Dossier-scoped routes live under the case
 * (`credit-cases/{id}/workflow`) and are opened to every review direction by
 * SecurityConfig; the service enforces which direction may actually act. The
 * cross-dossier queue and status batch live under `workflow`.
 */
@RestController
class WorkflowController(
    private val workflow: WorkflowService,
    private val currentUser: CurrentUser,
) {
    @GetMapping("/credit-cases/{caseId}/workflow")
    fun state(
        @PathVariable caseId: UUID,
    ): WorkflowStateResponse = workflow.state(caseId, currentUser.id())

    @PostMapping("/credit-cases/{caseId}/workflow/actions")
    fun act(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: WorkflowActionRequest,
    ): WorkflowStateResponse = workflow.act(caseId, currentUser.id(), request.action, request.comment)

    @GetMapping("/workflow/queue")
    fun queue(): List<QueueItemResponse> = workflow.queueFor(currentUser.id())

    @GetMapping("/workflow/statuses")
    fun statuses(
        @RequestParam(name = "caseIds") caseIds: List<UUID>,
    ): List<CaseWorkflowStatusResponse> = workflow.statuses(caseIds)
}
