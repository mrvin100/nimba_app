package com.nimba.analysissheet

/**
 * How a Fiche d'analyse section is rendered and where its content lives —
 * matching the "hardcoded typed sections, no generic form engine" decision
 * (docs/nimba-fa-document-spec.md §3). Every editable type stores its content
 * as opaque JSON in [com.nimba.analysissheet.internal.AnalysisSheetSection];
 * the frontend defines the exact shape per key and the docx export is the only
 * backend reader:
 *
 * - [NARRATIVE] — plain text (a textarea).
 * - [TABLE] — typed repeatable rows with fixed columns per key, plus an
 *   optional trailing `commentaire` text (`{"rows":[...],"commentaire":"…"}`).
 * - [KEY_VALUE] — a fixed set of labeled fields (`{"champ":"valeur",…}`).
 * - [FLEX_TABLE] — user-defined column headers + rows, for tables whose
 *   columns differ per dossier (Pilier 3 hypothèses), plus optional intro
 *   narrative and commentaire (`{"narrative":"…","columns":[…],"rows":[[…]],"commentaire":"…"}`).
 * - [FINANCIAL] — fixed SYSCOHADA rubrique rows × year columns
 *   (`{"years":["2024","2025"],"rows":{"RUBRIQUE":["…","…"]},"commentaire":"…"}`).
 * - [IMAGE] — narrative text plus uploaded figures
 *   (`{"narrative":"…","images":[{"attachmentId":"…","caption":"…"}],"commentaire":"…"}`).
 * - [COMPUTED] — derived read-only from the TA and conditions de banque.
 * - [BOUND] — renders an existing dossier-level entity (identité, garanties,
 *   conditions de banque, articulation) that already has its own screen.
 */
enum class FaSectionType {
    NARRATIVE,
    TABLE,
    KEY_VALUE,
    FLEX_TABLE,
    FINANCIAL,
    IMAGE,
    COMPUTED,
    BOUND,
    ;

    /** Whether this section type persists its own content, as opposed to being derived or bound. */
    val isEditable: Boolean
        get() = this != COMPUTED && this != BOUND
}
