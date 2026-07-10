package com.nimba.analysissheet

import java.util.UUID

/**
 * The Fiche d'analyse module's public API. Other modules — and this module's own
 * web layer — read and write a case's FA through this interface only, never
 * through the repository or entity.
 */
interface AnalysisSheetModuleApi {
    fun findByCase(creditCaseId: UUID): AnalysisSheetInfo?

    /** Opens the FA in DRAFT. 409 if one already exists for the case. */
    fun create(command: CreateAnalysisSheetCommand): AnalysisSheetInfo

    /** Replaces the draft content. 409 if the FA is already PUBLISHED. */
    fun updateDraft(
        creditCaseId: UUID,
        content: String?,
    ): AnalysisSheetInfo

    /** Locks the FA. 409 if already PUBLISHED. */
    fun publish(creditCaseId: UUID): AnalysisSheetInfo

    /**
     * Reopens a published FA for editing (PUBLISHED → DRAFT), called by the workflow
     * module when a dossier is returned to the DRI for changes. A no-op when the case
     * has no FA or it is already a draft.
     */
    fun reopen(creditCaseId: UUID)
}
