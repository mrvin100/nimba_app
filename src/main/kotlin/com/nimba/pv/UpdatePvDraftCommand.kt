package com.nimba.pv

import java.time.LocalDate

/** Replaces a draft PV's editable fields wholesale (see [PvModuleApi.updateDraft]). */
data class UpdatePvDraftCommand(
    val seanceDate: LocalDate,
    val rapporteur: String?,
    val president: String?,
    val pointsForts: String?,
    val pointsFaibles: String?,
    val debats: List<PvDebat>,
)
