package com.nimba.pv

import java.time.LocalDate

/**
 * Replaces a draft PV's editable fields wholesale (see [PvModuleApi.updateDraft]).
 * Points forts/faibles are not here — they are read from the FA at finalization,
 * never typed on the PV (real-document analysis, 2026-07-13).
 */
data class UpdatePvDraftCommand(
    val seanceDate: LocalDate,
    val rapporteur: String?,
    val president: String?,
    val debats: List<PvDebat>,
)
