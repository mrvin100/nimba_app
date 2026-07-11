package com.nimba.analysissheet

/**
 * How a Fiche d'analyse section is rendered and where its content lives —
 * matching the "hardcoded typed sections, no generic form engine" decision
 * (docs/nimba-credit-workflow-design.md §10.2). Only [NARRATIVE] and [TABLE]
 * store anything in [com.nimba.analysissheet.internal.AnalysisSheetSection];
 * [COMPUTED] is derived read-only from the TA and conditions de banque,
 * [BOUND] renders an existing dossier-level entity (identité, garanties,
 * conditions de banque, articulation) that already has its own screen.
 */
enum class FaSectionType {
    NARRATIVE,
    TABLE,
    COMPUTED,
    BOUND,
    ;

    /** Whether this section type persists its own content, as opposed to being derived or bound. */
    val isEditable: Boolean
        get() = this == NARRATIVE || this == TABLE
}
