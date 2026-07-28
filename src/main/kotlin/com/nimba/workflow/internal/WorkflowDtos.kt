package com.nimba.workflow.internal

import com.nimba.creditcase.ContractType
import com.nimba.creditcase.ProductType
import com.nimba.identity.Department
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.WorkflowStatus
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.util.UUID

/** Body of a workflow transition; [comment] is mandatory for the return/reject actions. */
data class WorkflowActionRequest(
    @field:NotNull
    val action: WorkflowAction,
    val comment: String? = null,
)

/** The dossier's workflow state as the review screens render it. */
data class WorkflowStateResponse(
    val creditCaseId: UUID,
    val status: WorkflowStatus,
    /** Actions the current caller may take right now (empty when it is not their turn). */
    val availableActions: List<WorkflowAction>,
    /** Distinct comité approvals gathered in the current cycle (0..required). */
    val comiteApprovals: Int,
    val comiteApprovalsRequired: Int,
    /** A short explanation when the caller's expected action is blocked (e.g. FA not published). */
    val hint: String?,
    val timeline: List<WorkflowEventResponse>,
)

data class WorkflowEventResponse(
    val id: UUID,
    val actorId: UUID,
    val actorName: String,
    val actorDepartment: Department,
    val action: WorkflowAction,
    val fromStatus: WorkflowStatus,
    val toStatus: WorkflowStatus,
    val comment: String?,
    val occurredAt: Instant,
)

/**
 * One dossier awaiting the caller's review, for a direction's queue. Carries the
 * same descriptive fields as `CreditCaseSummaryResponse` (product, client) so the
 * frontend can render the queue and the full dossier list through the same table
 * and columns, switching only the data source.
 */
data class QueueItemResponse(
    val creditCaseId: UUID,
    val caseNumber: String,
    val clientId: UUID,
    val clientName: String,
    val productType: ProductType,
    val contractType: ContractType?,
    val status: WorkflowStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Batch lookup row: a dossier's workflow status (drives the DRI dashboard badges). */
data class CaseWorkflowStatusResponse(
    val creditCaseId: UUID,
    val status: WorkflowStatus,
)
