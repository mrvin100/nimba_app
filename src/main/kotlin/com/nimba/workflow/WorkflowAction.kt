package com.nimba.workflow

/**
 * A move an actor can make on a dossier. The workflow service maps each action to a
 * status transition given the dossier's current state and the actor's direction.
 * REQUEST_CHANGES, REQUEST_COMPLETION, REJECT and ARCHIVE require a comment.
 */
enum class WorkflowAction {
    /** DRI sends a constituted dossier into review (BROUILLON → EN_REVUE_DCM). */
    SUBMIT,

    /** DRI submits the applied corrections (CORRECTIONS_DRI / A_COMPLETER → EN_VERIFICATION_DCM). */
    SUBMIT_CORRECTIONS,

    /** A reviewer approves this stage (DCM → DRC; DRC avis favorable → vérification DCM; comité needs two). */
    APPROVE,

    /**
     * Returns the dossier to the DRI: from EN_REVUE_DCM back to BROUILLON (the
     * review loop), from EN_REVUE_DRC or EN_VERIFICATION_DCM to CORRECTIONS_DRI
     * (the post-DRC lane).
     */
    REQUEST_CHANGES,

    /** The comité returns the dossier to the DRI to add documents / restructure (→ A_COMPLETER). */
    REQUEST_COMPLETION,

    /** DCM sends the verified dossier to the comité (EN_VERIFICATION_DCM → PRET_POUR_COMITE). */
    SEND_TO_COMITE,

    /** The comité rejects the dossier; it goes to the DCM for explicit archiving (→ EN_ARCHIVAGE). */
    REJECT,

    /** DCM records the final archiving note after a comité rejection (→ REJETE, dossier archivé). */
    ARCHIVE,
}
