package com.nimba.workflow

import java.util.UUID

/**
 * The workflow module's public API. Other modules read a dossier's lifecycle status
 * through this interface only. Kept minimal: later epics gate document generation on
 * the dossier being APPROUVE.
 */
interface WorkflowModuleApi {
    /** The dossier's current lifecycle status, or null if it has no workflow yet. */
    fun statusOf(creditCaseId: UUID): WorkflowStatus?

    /**
     * Executes a workflow action on behalf of [actorId] — same gates as the
     * HTTP action endpoint (status, direction, comment requirements). Used by
     * the review module so a submitted review triggers its transition
     * atomically. Returns the resulting status.
     */
    fun act(
        creditCaseId: UUID,
        actorId: UUID,
        action: WorkflowAction,
        comment: String?,
    ): WorkflowStatus
}
