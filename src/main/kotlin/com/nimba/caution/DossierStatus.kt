package com.nimba.caution

/**
 * A caution dossier's lifecycle. The dossier is the aggregate root, so the lock
 * is at this level (not per document):
 *
 *  BROUILLON ──finalize──► FINALISE ──proroge (Manager)──► EN_PROROGATION
 *      ▲                        ▲                                │
 *      └──── (édition libre)    └────────── refinalize ──────────┘
 *
 * - [BROUILLON] : the request is being constituted; documents and common
 *   information are freely added, edited and deleted.
 * - [FINALISE] : the request is finalized — everything is frozen (a write gate
 *   refuses any change) and each document's client snapshot is captured.
 * - [EN_PROROGATION] : a Manager reopened the finalized dossier to correct a
 *   single document (deadline extension…); edits are allowed again until it is
 *   re-finalized, and the reason + responsible are journaled.
 */
enum class DossierStatus {
    BROUILLON,
    FINALISE,
    EN_PROROGATION,
}
