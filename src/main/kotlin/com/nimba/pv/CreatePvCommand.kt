package com.nimba.pv

import java.time.LocalDate
import java.util.UUID

/** Opens a dossier's PV in DRAFT (see [PvModuleApi.create]). */
data class CreatePvCommand(
    val creditCaseId: UUID,
    val createdBy: UUID,
    val seanceDate: LocalDate,
)
