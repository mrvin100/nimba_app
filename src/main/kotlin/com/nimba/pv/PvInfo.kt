package com.nimba.pv

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Read-only view of a case's PV, safe to share across module boundaries. */
data class PvInfo(
    val id: UUID,
    val creditCaseId: UUID,
    val status: PvStatus,
    val seanceDate: LocalDate,
    val rapporteur: String?,
    val president: String?,
    val pointsForts: String?,
    val pointsFaibles: String?,
    val debats: List<PvDebat>,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
    val finalizedAt: Instant?,
    /** Frozen dossier data; populated only once [status] is FINAL. */
    val snapshot: PvSnapshot?,
)
