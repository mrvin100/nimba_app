package com.nimba.guarantee

import java.util.UUID

data class CreateGuaranteeCommand(
    val creditCaseId: UUID,
    val kind: GuaranteeKind,
    val description: String,
    val createdBy: UUID,
)

data class UpdateGuaranteeCommand(
    val kind: GuaranteeKind,
    val description: String,
)
