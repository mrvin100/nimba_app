package com.nimba.workflow.internal

import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.identity.Department
import com.nimba.identity.IdentityModuleApi
import com.nimba.notification.NotificationModuleApi
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
    private val notifications: NotificationModuleApi,
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
        if (to != from) {
            val caseNumber = creditCases.findById(creditCaseId)?.caseNumber ?: creditCaseId.toString()
            notifyNextActor(creditCaseId, caseNumber, to)
        }
        return workflow.toState(callerId, identity.departmentsOf(callerId))
    }

    /** Tells whoever must act next that the dossier is waiting on them (design §12.1). */
    private fun notifyNextActor(
        creditCaseId: UUID,
        caseNumber: String,
        to: WorkflowStatus,
    ) {
        when (to) {
            WorkflowStatus.EN_REVUE_DCM ->
                notifications.notifyDepartment(Department.DCM, creditCaseId, "Dossier $caseNumber soumis pour revue")
            WorkflowStatus.EN_REVUE_DRC ->
                notifications.notifyDepartment(Department.DRC, creditCaseId, "Dossier $caseNumber approuvé par la DCM, à votre analyse")
            WorkflowStatus.CORRECTIONS_DRI -> {
                notifications.notifyDepartment(Department.DRI, creditCaseId, "Dossier $caseNumber : observations à traiter")
                notifications.notifyDepartment(
                    Department.DCM,
                    creditCaseId,
                    "Dossier $caseNumber : copie des observations transmises au DRI",
                )
            }
            WorkflowStatus.A_COMPLETER -> {
                notifications.notifyDepartment(
                    Department.DRI,
                    creditCaseId,
                    "Dossier $caseNumber à compléter selon les observations du comité",
                )
                notifications.notifyDepartment(Department.DCM, creditCaseId, "Dossier $caseNumber renvoyé par le comité pour complément")
            }
            WorkflowStatus.EN_VERIFICATION_DCM ->
                notifications.notifyDepartment(Department.DCM, creditCaseId, "Dossier $caseNumber à vérifier avant envoi au comité")
            WorkflowStatus.PRET_POUR_COMITE ->
                notifications.notifyDepartment(Department.COMITE, creditCaseId, "Dossier $caseNumber prêt pour le comité")
            WorkflowStatus.APPROUVE -> {
                notifications.notifyDepartment(Department.DCM, creditCaseId, "Dossier $caseNumber approuvé par le comité")
                notifications.notifyDepartment(Department.DRI, creditCaseId, "Dossier $caseNumber approuvé par le comité")
            }
            WorkflowStatus.EN_ARCHIVAGE ->
                notifications.notifyDepartment(
                    Department.DCM,
                    creditCaseId,
                    "Dossier $caseNumber rejeté par le comité — archivage à finaliser",
                )
            WorkflowStatus.REJETE -> {
                notifications.notifyDepartment(Department.DRI, creditCaseId, "Dossier $caseNumber archivé après rejet du comité")
                notifications.notifyDepartment(Department.DCM, creditCaseId, "Dossier $caseNumber archivé")
            }
            WorkflowStatus.BROUILLON ->
                notifications.notifyDepartment(Department.DRI, creditCaseId, "Dossier $caseNumber renvoyé pour modifications")
            else -> {}
        }
    }

    @Transactional(readOnly = true)
    fun queueFor(callerId: UUID): List<QueueItemResponse> {
        val departments = identity.departmentsOf(callerId)
        return departments
            .flatMap { reviewedStatuses(it) }
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

    /** Applies the action's side effects and returns the resulting status (design §12.1). */
    private fun apply(
        workflow: CaseWorkflow,
        callerId: UUID,
        action: WorkflowAction,
    ): WorkflowStatus =
        when (action) {
            WorkflowAction.SUBMIT -> {
                requirePublishedFa(workflow.creditCaseId)
                WorkflowStatus.EN_REVUE_DCM
            }

            WorkflowAction.SUBMIT_CORRECTIONS -> {
                requirePublishedFa(workflow.creditCaseId)
                WorkflowStatus.EN_VERIFICATION_DCM
            }

            WorkflowAction.APPROVE ->
                when (workflow.status) {
                    WorkflowStatus.EN_REVUE_DCM -> WorkflowStatus.EN_REVUE_DRC
                    // DRC's avis favorable: straight to the DCM verification —
                    // the DRC analyses the dossier only once.
                    WorkflowStatus.EN_REVUE_DRC -> WorkflowStatus.EN_VERIFICATION_DCM
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

            WorkflowAction.REQUEST_CHANGES ->
                when (workflow.status) {
                    // The DCM review loop restarts the dossier from the draft.
                    WorkflowStatus.EN_REVUE_DCM -> returnToDri(workflow, WorkflowStatus.BROUILLON)
                    // DRC observations (and a DCM verification bounce) stay in
                    // the post-DRC lane: the DRI corrects, the DCM verifies.
                    WorkflowStatus.EN_REVUE_DRC, WorkflowStatus.EN_VERIFICATION_DCM ->
                        returnToDri(workflow, WorkflowStatus.CORRECTIONS_DRI)
                    else -> throw conflict("Cette action n'est pas permise à ce stade du dossier")
                }

            WorkflowAction.REQUEST_COMPLETION -> returnToDri(workflow, WorkflowStatus.A_COMPLETER)

            WorkflowAction.SEND_TO_COMITE -> WorkflowStatus.PRET_POUR_COMITE

            WorkflowAction.REJECT -> {
                // The comité's rejection sends the dossier to the DCM, who
                // records the final archiving note (explicit ARCHIVE step).
                workflow.comiteApprovers.clear()
                WorkflowStatus.EN_ARCHIVAGE
            }

            WorkflowAction.ARCHIVE -> {
                creditCases.archive(workflow.creditCaseId)
                WorkflowStatus.REJETE
            }
        }

    private fun requirePublishedFa(creditCaseId: UUID) {
        if (analysisSheets.findByCase(creditCaseId)?.status != AnalysisSheetStatus.PUBLISHED) {
            throw conflict("La fiche d'analyse doit être publiée avant de soumettre le dossier à la revue")
        }
    }

    /** Any return to a DRI-owned status clears the comité votes and reopens the FA for editing. */
    private fun returnToDri(
        workflow: CaseWorkflow,
        to: WorkflowStatus,
    ): WorkflowStatus {
        workflow.comiteApprovers.clear()
        analysisSheets.reopen(workflow.creditCaseId)
        return to
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
        val driEditingStatuses =
            setOf(WorkflowStatus.BROUILLON, WorkflowStatus.CORRECTIONS_DRI, WorkflowStatus.A_COMPLETER)
        val hint =
            if (actionable && status in driEditingStatuses && available.isEmpty()) {
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
            WorkflowStatus.CORRECTIONS_DRI, WorkflowStatus.A_COMPLETER ->
                if (analysisSheets.findByCase(creditCaseId)?.status == AnalysisSheetStatus.PUBLISHED) {
                    listOf(WorkflowAction.SUBMIT_CORRECTIONS)
                } else {
                    emptyList()
                }
            WorkflowStatus.EN_REVUE_DCM, WorkflowStatus.EN_REVUE_DRC ->
                listOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_CHANGES)
            WorkflowStatus.EN_VERIFICATION_DCM ->
                listOf(WorkflowAction.SEND_TO_COMITE, WorkflowAction.REQUEST_CHANGES)
            WorkflowStatus.PRET_POUR_COMITE ->
                if (callerId in comiteApprovers) {
                    emptyList()
                } else {
                    listOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_COMPLETION, WorkflowAction.REJECT)
                }
            WorkflowStatus.EN_ARCHIVAGE -> listOf(WorkflowAction.ARCHIVE)
            else -> emptyList()
        }

    private fun allowedActions(status: WorkflowStatus): Set<WorkflowAction> =
        when (status) {
            WorkflowStatus.BROUILLON -> setOf(WorkflowAction.SUBMIT)
            WorkflowStatus.CORRECTIONS_DRI, WorkflowStatus.A_COMPLETER -> setOf(WorkflowAction.SUBMIT_CORRECTIONS)
            WorkflowStatus.EN_REVUE_DCM, WorkflowStatus.EN_REVUE_DRC ->
                setOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_CHANGES)
            WorkflowStatus.EN_VERIFICATION_DCM -> setOf(WorkflowAction.SEND_TO_COMITE, WorkflowAction.REQUEST_CHANGES)
            WorkflowStatus.PRET_POUR_COMITE ->
                setOf(WorkflowAction.APPROVE, WorkflowAction.REQUEST_COMPLETION, WorkflowAction.REJECT)
            WorkflowStatus.EN_ARCHIVAGE -> setOf(WorkflowAction.ARCHIVE)
            else -> emptySet()
        }

    /** The direction whose turn it is to act at a given status, or null at a terminal state. */
    private fun reviewDepartment(status: WorkflowStatus): Department? =
        when (status) {
            WorkflowStatus.BROUILLON, WorkflowStatus.CORRECTIONS_DRI, WorkflowStatus.A_COMPLETER -> Department.DRI
            WorkflowStatus.EN_REVUE_DCM, WorkflowStatus.EN_VERIFICATION_DCM, WorkflowStatus.EN_ARCHIVAGE -> Department.DCM
            WorkflowStatus.EN_REVUE_DRC -> Department.DRC
            WorkflowStatus.PRET_POUR_COMITE -> Department.COMITE
            else -> null
        }

    /** The statuses a direction acts on (inverse of [reviewDepartment]), for the queues. */
    private fun reviewedStatuses(department: Department): List<WorkflowStatus> =
        when (department) {
            Department.DRI -> listOf(WorkflowStatus.BROUILLON, WorkflowStatus.CORRECTIONS_DRI, WorkflowStatus.A_COMPLETER)
            Department.DCM ->
                listOf(WorkflowStatus.EN_REVUE_DCM, WorkflowStatus.EN_VERIFICATION_DCM, WorkflowStatus.EN_ARCHIVAGE)
            Department.DRC -> listOf(WorkflowStatus.EN_REVUE_DRC)
            Department.COMITE -> listOf(WorkflowStatus.PRET_POUR_COMITE)
        }

    private fun conflict(message: String) = ResponseStatusException(HttpStatus.CONFLICT, message)

    private val WorkflowAction.requiresComment: Boolean
        get() =
            this in
                setOf(
                    WorkflowAction.REQUEST_CHANGES,
                    WorkflowAction.REQUEST_COMPLETION,
                    WorkflowAction.REJECT,
                    WorkflowAction.ARCHIVE,
                )
}
