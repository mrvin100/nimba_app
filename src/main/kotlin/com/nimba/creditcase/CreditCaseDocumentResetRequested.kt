package com.nimba.creditcase

import java.util.UUID

/** A dossier document a DRI manager (or admin) can wipe from the Settings tab while the dossier is BROUILLON. */
enum class ResettableDocument {
    AMORTISSEMENT,
    FICHE_ANALYSE,
    GARANTIES,
    PV,
    FMP,
}

/**
 * Published when a critical Settings action wipes ONE document of a dossier
 * (design §12.3) — same event-driven fan-out as [CreditCaseDeleted]: each
 * module resets its own data in a listener, so the publisher (the workflow
 * module, which owns the BROUILLON gate) never depends on the document
 * modules (pv depends on workflow — a direct call would be a cycle).
 */
data class CreditCaseDocumentResetRequested(
    val creditCaseId: UUID,
    val document: ResettableDocument,
)
