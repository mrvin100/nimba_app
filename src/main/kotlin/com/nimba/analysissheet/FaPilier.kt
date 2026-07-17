package com.nimba.analysissheet

/**
 * The Fiche d'analyse's top-level groups, rendered as one tab each. PILIER_2 is
 * entirely variant-specific (présentation du contrat for AVEC_CONTRAT, analyse
 * du marché for SANS_CONTRAT); ANNEXES only exists for SANS_CONTRAT (payer
 * financials + client list). Order here is the document order of the exported
 * FA (docs/nimba-fa-document-spec.md §2).
 */
enum class FaPilier {
    COVER,
    PILIER_1,
    PILIER_2,
    PILIER_3,
    PILIER_4,
    CONCLUSION,
    ANNEXES,
}
