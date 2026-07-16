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
}
