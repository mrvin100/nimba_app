package com.nimba.review

/**
 * The outcome of a submitted FA review — GitHub-PR semantics mapped onto the
 * validation flow (design §12.2). The DCM's verdicts drive the review loop
 * (APPROUVE / CHANGEMENTS_DEMANDES); the DRC's single pass ends in
 * AVIS_FAVORABLE (straight to the DCM verification) or OBSERVATIONS (the
 * dossier goes to the DRI corrections lane, DCM in copy).
 */
enum class ReviewVerdict {
    /** DCM — the dossier advances to the DRC analysis. */
    APPROUVE,

    /** DCM — back to the DRI; the review loop restarts once corrected. */
    CHANGEMENTS_DEMANDES,

    /** DRC — no blocking observations; straight to the DCM verification. */
    AVIS_FAVORABLE,

    /** DRC — observations to apply; the DRI corrects, the DCM verifies. */
    OBSERVATIONS,
}
