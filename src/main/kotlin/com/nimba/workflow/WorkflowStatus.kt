package com.nimba.workflow

/**
 * The dossier's cross-directorate lifecycle (distinct from the amortization module's
 * internal `CreditCaseStatus`, which only tracks the TA sub-state). Declaration order
 * follows the process. Values from EN_SIGNATURE onward are reached by later epics
 * (signature, vie du crédit); they are declared now so the type is complete and no
 * migration is needed when those transitions are wired.
 */
enum class WorkflowStatus {
    /** DRI is constituting the dossier (TA upload, FA draft). */
    BROUILLON,

    /** Submitted; awaiting the DCM review. */
    EN_REVUE_DCM,

    /** DCM approved; awaiting the DRC (risk & compliance) review. */
    EN_REVUE_DRC,

    /** DRC approved; awaiting two distinct comité approvals. */
    PRET_POUR_COMITE,

    /** Approved by the comité; the DCM can now generate the PV. */
    APPROUVE,

    /** Rejected by the comité; the dossier is archived with the reason. */
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
