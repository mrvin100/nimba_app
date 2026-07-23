package com.nimba.caution

import java.time.Instant
import java.util.UUID

/** A journaled dossier lifecycle transition. */
enum class DossierAction {
    FINALIZE,
    PROROGE,
    REFINALIZE,
}

/**
 * One entry of a dossier's lifecycle journal: who did what, when, and why (the
 * reason is required for a prorogation). Same intent as the workflow module's
 * event log — a full, immutable audit of the request's finalization history.
 */
data class CautionDossierEventInfo(
    val id: UUID,
    val action: DossierAction,
    val fromStatus: DossierStatus,
    val toStatus: DossierStatus,
    val reason: String?,
    val actor: UUID,
    val createdAt: Instant,
)
