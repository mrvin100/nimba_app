package com.nimba.workflow.internal

import com.nimba.identity.Department
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.WorkflowStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One append-only entry of a dossier's workflow timeline: who did what, when, and
 * how it moved the status. This is the human-readable dossier history shown on the
 * detail page — distinct from the cross-cutting HTTP audit trail. [toStatus] may
 * equal [fromStatus] (the first of two comité approvals records the vote without yet
 * flipping the status).
 */
@Entity
@Table(name = "workflow_event")
class WorkflowEvent(
    @Column(name = "credit_case_id", nullable = false, updatable = false)
    val creditCaseId: UUID,
    @Column(name = "actor_id", nullable = false, updatable = false)
    val actorId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "actor_department", nullable = false, updatable = false)
    val actorDepartment: Department,
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    val action: WorkflowAction,
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, updatable = false)
    val fromStatus: WorkflowStatus,
    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, updatable = false)
    val toStatus: WorkflowStatus,
    @Column(name = "comment", columnDefinition = "TEXT")
    val comment: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: Instant = Instant.now()
}
