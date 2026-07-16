package com.nimba.workflow

/**
 * A move an actor can make on a dossier. The workflow service maps each action to a
 * status transition given the dossier's current state and the actor's direction.
 * REQUEST_CHANGES, REQUEST_COMPLETION and REJECT require a comment (the reason).
 */
enum class WorkflowAction {
    /** DRI sends a constituted dossier into review (BROUILLON → EN_REVUE_DCM). */
    SUBMIT,

    /** A reviewer approves this stage (advances the dossier; comité needs two). */
    APPROVE,

    /** DCM/DRC returns the dossier to the DRI for changes. */
    REQUEST_CHANGES,

    /** The comité returns the dossier to the DRI to add documents / restructure. */
    REQUEST_COMPLETION,

    /** The comité rejects the dossier; it is archived with the reason. */
    REJECT,
}
