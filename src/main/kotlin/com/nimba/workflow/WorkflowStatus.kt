package com.nimba.workflow

/**
 * The dossier's cross-directorate lifecycle (distinct from the amortization module's
 * internal `CreditCaseStatus`, which only tracks the TA sub-state). Declaration order
 * follows the process (design §12.1). Values from EN_SIGNATURE onward are reached by
 * later epics (signature, vie du crédit); they are declared now so the type is
 * complete and no migration is needed when those transitions are wired.
 */
enum class WorkflowStatus {
    /** DRI is constituting the dossier (TA upload, FA draft). */
    BROUILLON,

    /** Submitted; awaiting the DCM review (approve / request changes loop). */
    EN_REVUE_DCM,

    /** DCM approved; awaiting the DRC's single analysis pass. */
    EN_REVUE_DRC,

    /** The DRC left observations; DRI applies them (DCM holds a copy). */
    CORRECTIONS_DRI,

    /** The comité returned the dossier; DRI completes per its observations. */
    A_COMPLETER,

    /** DRI's corrections submitted; DCM verifies and is the only one who sends to the comité. */
    EN_VERIFICATION_DCM,

    /** Sent by the DCM; awaiting two distinct comité approvals. */
    PRET_POUR_COMITE,

    /** Approved by the comité; the DCM can now generate the PV. */
    APPROUVE,

    /** Rejected by the comité; awaiting the DCM's explicit archiving note. */
    EN_ARCHIVAGE,

    /** Archived by the DCM after the comité's rejection; full traces kept. */
    REJETE,

    /** DRI is collecting the client's signatures (EPIC-11). */
    EN_SIGNATURE,

    /** All signed documents uploaded; awaiting the DCM's verification (EPIC-11). */
    SIGNE,

    /** Mise en place done; repayment running (EPIC-12). */
    EN_COURS,

    /** Fully repaid and closed (EPIC-12). */
    CLOTURE,
}
