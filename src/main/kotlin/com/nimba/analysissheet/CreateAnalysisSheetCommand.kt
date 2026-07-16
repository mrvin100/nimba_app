package com.nimba.analysissheet

import java.util.UUID

/**
 * Opens the Fiche d'analyse for a case, in DRAFT. The FA variant is not
 * client-supplied — the service resolves it from the case's own (productType,
 * contractType) through [com.nimba.creditcase.CaseTypePolicies], the single source
 * of truth for that mapping.
 */
data class CreateAnalysisSheetCommand(
    val creditCaseId: UUID,
    val createdBy: UUID,
)
