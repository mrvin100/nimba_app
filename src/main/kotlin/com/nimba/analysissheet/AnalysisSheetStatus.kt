package com.nimba.analysissheet

/**
 * DRAFT is editable by the DRI; publishing locks it and signals the dossier is
 * ready for DCM's review. Nothing reopens a published sheet yet — that belongs to
 * the workflow's "modifications demandées" return-to-DRI step (a later epic).
 */
enum class AnalysisSheetStatus {
    DRAFT,
    PUBLISHED,
}
