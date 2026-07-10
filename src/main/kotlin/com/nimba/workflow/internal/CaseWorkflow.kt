package com.nimba.workflow.internal

import com.nimba.workflow.WorkflowStatus
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * The lifecycle state of one dossier. [creditCaseId] references the credit-case
 * module's aggregate by id only — no JPA relationship crosses the module boundary.
 * [comiteApprovers] holds the distinct comité members who have approved in the
 * CURRENT review cycle; it is cleared whenever the dossier returns to the DRI, so a
 * fresh round of two approvals is required after any change.
 */
@Entity
@Table(name = "case_workflow")
class CaseWorkflow(
    @Column(name = "credit_case_id", nullable = false, unique = true, updatable = false)
    val creditCaseId: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: WorkflowStatus = WorkflowStatus.BROUILLON

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "case_workflow_comite_approver", joinColumns = [JoinColumn(name = "case_workflow_id")])
    @Column(name = "approver_id", nullable = false)
    var comiteApprovers: MutableSet<UUID> = mutableSetOf()

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
