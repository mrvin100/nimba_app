package com.nimba.workflow.internal

import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.identity.Department
import com.nimba.identity.IdentityModuleApi
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.WorkflowStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * The dossier lifecycle state machine (design §3). It is the ONLY writer of workflow
 * state: a transition validates the dossier's current status, the caller's direction
 * and any preconditions (FA published to submit, two distinct comité approvals), then
 * records an immutable [WorkflowEvent]. Any return to the DRI clears the comité
 * approvals and reopens the FA, so a changed dossier re-earns the full review chain.
 */
@Service
class WorkflowService(
    private val workflows: CaseWorkflowRepository,
    private val events: WorkflowEventRepository,
    private val creditCases: CreditCaseModuleApi,
    private val analysisSheets: AnalysisSheetModuleApi,
    private val identity: IdentityModuleApi,
) {
    private companion object {
        const val COMITE_APPROVALS_REQUIRED = 2
    }

    @Transactional
    fun initialize(creditCaseId: UUID) {
        if (!workflows.existsByCreditCaseId(creditCaseId)) {
            workflows.save(CaseWorkflow(creditCaseId))
        }
    }

    @Transactional
    fun purge(creditCaseId: UUID) {
        events.deleteByCreditCaseId(creditCaseId)
        workflows.deleteByCreditCaseId(creditCaseId)
    }

    @Transactional(readOnly = true)
    fun state(
        creditCaseId: UUID,
        callerId: UUID,
    ): WorkflowStateResponse {
        val workflow = requireWorkflow(creditCaseId)
        return workflow.toState(callerId, identity.departmentsOf(callerId))
    }

    @Transactional
    fun act(
        creditCaseId: UUID,
        callerId: UUID,
        action: WorkflowAction,
        comment: String?,
    ): WorkflowStateResponse {
        val workflow = requireWorkflow(creditCaseId)
        val actingDepartment =
            reviewDepartment(workflow.status)
                ?: throw conflict("Aucune action de revue n'est disponible à ce stade du dossier")
        if (actingDepartment !in identity.departmentsOf(callerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Vous n'appartenez pas à la direction en charge de cette étape")
        }
        if (action !in allowedActions(workflow.status)) {
            throw conflict("Cette action n'est pas permise à ce stade du dossier")
        }
        if (action.requiresComment && comment.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Un commentaire est requis pour cette action")
        }

        val from = workflow.status
        val to = apply(workflow, callerId, action)
        workflow.status = to
        workflow.updatedAt = Instant.now()
        events.save(
            WorkflowEvent(
                creditCaseId = creditCaseId,
                actorId = callerId,
                actorDepartment = actingDepartment,
                action = action,
                fromStatus = from,
                toStatus = to,
                comment = comment?.takeIf { it.isNotBlank() },
            ),
        )
        return workflow.toState(callerId, identity.departmentsOf(callerId))
    }

    @Transactional(readOnly = true)
    fun queueFor(callerId: UUID): List<QueueItemResponse> {
        val departments = identity.departmentsOf(callerId)
        return departments
            .mapNotNull { reviewedStatus(it) }
            .distinct()
            .flatMap { status -> workflows.findByStatus(status) }
            // A comité member's queue excludes dossiers they have already voted on.
            .filterNot { it.status == WorkflowStatus.PRET_POUR_COMITE && callerId in it.comiteApprovers }
            .mapNotNull { workflow ->
                creditCases.findById(workflow.creditCaseId)?.let { case ->
                    QueueItemResponse(
                        creditCaseId = workflow.creditCaseId,
                        caseNumber = case.caseNumber,
                        clientName = case.clientName,
                        status = workflow.status,
                        updatedAt = workflow.updatedAt,
                    )
                }
            }.sortedBy { it.updatedAt }
    }

    @Transactional(readOnly = true)
    fun statuses(creditCaseIds: List<UUID>): List<CaseWorkflowStatusResponse> =
        creditCaseIds
            .mapNotNull { id -> workflows.findByCreditCaseId(id) }
            .map { CaseWorkflowStatusResponse(it.creditCaseId, it.status) }

    /** Applies the action's side effects and returns the resulting status. */
    private fun apply(
        workflow: CaseWorkflow,
        callerId: UUID,
        action: WorkflowAction,
    ): WorkflowStatus =
        when (action) {
            WorkflowAction.SUBMIT -> {
                if (analysisSheets.findByCase(workflow.creditCaseId)?.status != AnalysisSheetStatus.PUBLISHED) {
                    throw conflict("La fiche d'analyse doit être publiée avant de soumettre le dossier à la revue")
                }
                WorkflowStatus.EN_REVUE_DCM
            }

            WorkflowAction.APPROVE ->
                when (workflow.status) {
                    WorkflowStatus.EN_REVUE_DCM -> WorkflowStatus.EN_REVUE_DRC
                    WorkflowStatus.EN_REVUE_DRC -> WorkflowStatus.PRET_POUR_COMITE
                    WorkflowStatus.PRET_POUR_COMITE -> {
                        if (callerId in workflow.comiteApprovers) {
                            throw conflict("Vous avez déjà approuvé ce dossier")
                        }
                        workflow.comiteApprovers.add(callerId)
                        if (workflow.comiteApprovers.size >= COMITE_APPROVALS_REQUIRED) {
                            WorkflowStatus.APPROUVE
                        } else {
                            WorkflowStatus.PRET_POUR_COMITE
                        }
                    }
                    else -> throw conflict("Cette action n'est pas permise à ce stade du dossier")
                }

            WorkflowAction.REQUEST_CHANGES, WorkflowAction.REQUEST_COMPLETION -> {
                // Back to the DRI: the review chain restarts from scratch after any change.
                workflow.comiteApprovers.clear()
                analysisSheets.reopen(workflow.creditCaseId)
                WorkflowStatus.BROUILLON
            }

            WorkflowAction.REJECT -> {
                creditCases.archive(workflow.creditCaseId)
                WorkflowStatus.REJETE
            }
        }

    private fun requireWorkflow(creditCaseId: UUID): CaseWorkflow {
        // Surfaces the case's canonical 404 when the dossier itself is unknown.
        creditCases.getOrThrow(creditCaseId)
        return workflows.findByCreditCaseId(creditCaseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun workflow pour ce dossier")
    }

    private fun CaseWorkflow.toState(
        callerId: UUID,
        callerDepartments: Set<Department>,
    ): WorkflowStateResponse {
        val timeline =
            events.findByCreditCaseIdOrderByOccurredAtAsc(creditCaseId).map { event ->
                WorkflowEventResponse(
                    id = requireNotNull(event.id),
                    actorId = event.actorId,
                    actorName = identity.findUser(event.actorId)?.fullName ?: "—",
                    actorDepartment = event.actorDepartment,
                    action = event.action,
                    fromStatus = event.fromStatus,
                    toStatus = event.toStatus,
                    comment = event.comment,
                    occurredAt = event.occurredAt,
                )
            }
        val expected = reviewDepartment(status)
        val actionable = expected != null && expected in callerDepartments
        val available = if (actionable) availableActions(callerId) else emptyList()
        val hint =
            if (actionable && status == WorkflowStatus.BROUILLON && available.isEmpty()) {
                "Publiez la fiche d'analyse pour pouvoir soumettre le dossier à la revue."
            } else {
                null
            }
        return WorkflowStateResponse(
            creditCaseId = creditCaseId,
            status = status,
            availableActions = available,
            comiteApprovals = comiteApprovers.size,
            comiteApprovalsRequired = COMITE_APPROVALS_REQUIRED,
            hint = hint,
            timeline = timeline,
        )
    }

    private fun CaseWorkflow.availableActions(callerId: UUID): List<WorkflowAction> =
        when (status) {
            WorkflowStatus.BROUILLON ->
                if (analysisSheets.findByCase(creditCaseId)?.status == AnalysisSheetStatus.PUBLISHED) {
                    listOf(WorkflowAction.SUBMIT)
                } else {
                    emptyList()
                }
            WorkflowStatus.EN_REVUE_DCM, WorkflowStatus.EN_REVUE_DRC ->
                listOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_CHANGES)
            WorkflowStatus.PRET_POUR_COMITE ->
                if (callerId in comiteApprovers) {
                    emptyList()
                } else {
                    listOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_COMPLETION, WorkflowAction.REJECT)
                }
            else -> emptyList()
        }

    private fun allowedActions(status: WorkflowStatus): Set<WorkflowAction> =
        when (status) {
            WorkflowStatus.BROUILLON -> setOf(WorkflowAction.SUBMIT)
            WorkflowStatus.EN_REVUE_DCM, WorkflowStatus.EN_REVUE_DRC ->
                setOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_CHANGES)
            WorkflowStatus.PRET_POUR_COMITE ->
                setOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_COMPLETION, WorkflowAction.REJECT)
            else -> emptySet()
        }

    /** The direction whose turn it is to act at a given status, or null at a terminal state. */
    private fun reviewDepartment(status: WorkflowStatus): Department? =
        when (status) {
            WorkflowStatus.BROUILLON -> Department.DRI
            WorkflowStatus.EN_REVUE_DCM -> Department.DCM
            WorkflowStatus.EN_REVUE_DRC -> Department.DRC
            WorkflowStatus.PRET_POUR_COMITE -> Department.COMITE
            else -> null
        }

    /** The status a direction reviews (inverse of [reviewDepartment]), for the queues. */
    private fun reviewedStatus(department: Department): WorkflowStatus? =
        when (department) {
            Department.DRI -> WorkflowStatus.BROUILLON
            Department.DCM -> WorkflowStatus.EN_REVUE_DCM
            Department.DRC -> WorkflowStatus.EN_REVUE_DRC
            Department.COMITE -> WorkflowStatus.PRET_POUR_COMITE
        }

    private fun conflict(message: String) = ResponseStatusException(HttpStatus.CONFLICT, message)

    private val WorkflowAction.requiresComment: Boolean
        get() = this in setOf(WorkflowAction.REQUEST_CHANGES, WorkflowAction.REQUEST_COMPLETION, WorkflowAction.REJECT)
}
