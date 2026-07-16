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

    /**
     * The case's FA sections, per [FaSectionRegistry] for its variant — empty
     * list if the case has no FA yet. Every applicable key is returned even when
     * nothing was saved to it, so the frontend always renders the full skeleton.
     */
    fun sections(creditCaseId: UUID): List<FaSectionInfo>

    /**
     * Replaces one editable section's content. 409 if the FA is already
     * PUBLISHED, 400 if [key] is not editable ([FaSectionType.isEditable]) or
     * does not apply to the case's variant.
     */
    fun updateSection(
        creditCaseId: UUID,
        key: FaSectionKey,
        contentJson: String?,
    ): FaSectionInfo

    /** Locks the FA. 409 if already PUBLISHED. */
    fun publish(creditCaseId: UUID): AnalysisSheetInfo

    /**
     * Reopens a published FA for editing (PUBLISHED → DRAFT), called by the workflow
     * module when a dossier is returned to the DRI for changes. A no-op when the case
     * has no FA or it is already a draft.
     */
    fun reopen(creditCaseId: UUID)
}
