package com.nimba.fmp

import java.util.UUID

/** Generates a case's FMP (see [FmpModuleApi.create]). */
data class CreateFmpCommand(
    val creditCaseId: UUID,
    val createdBy: UUID,
    val numeroPret: String,
    val garantieRef: String?,
)
