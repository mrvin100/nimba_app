package com.nimba.analysissheet

/**
 * One section of the Fiche d'analyse, per docs/nimba-credit-workflow-design.md
 * §10.2. This is the proof set built alongside the section framework itself
 * (both leasing variants' shared sections: cover, §3.4/§3.5, §4.2, conclusion,
 * plus one NARRATIVE and one TABLE section from Pilier 1) — the remaining ~12
 * sections (Pilier 1's other subsections, all of Pilier 2) are fast-follow work
 * using this exact pattern, once their content shape is confirmed with the user.
 */
enum class FaSectionKey(
    val pilier: FaPilier,
    val type: FaSectionType,
    val label: String,
) {
    COVER_PROPOSITION(FaPilier.COVER, FaSectionType.NARRATIVE, "Proposition de décision de l'analyste"),
    COVER_CONDITIONS_BANQUE(FaPilier.COVER, FaSectionType.BOUND, "Conditions de banque"),
    COVER_GARANTIES(FaPilier.COVER, FaSectionType.BOUND, "Garanties"),
    PILIER1_PERSONNES_CLES(FaPilier.PILIER_1, FaSectionType.TABLE, "1.6 Personnes clés"),
    PILIER1_SYNTHESE(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.16 Synthèse"),
    PILIER3_RENTABILITE_BANQUE(FaPilier.PILIER_3, FaSectionType.COMPUTED, "3.4 Rentabilité pour la banque"),
    PILIER3_SIMULATION_FINANCEMENT(FaPilier.PILIER_3, FaSectionType.COMPUTED, "3.5 Simulation du financement"),
    PILIER4_SURETES(FaPilier.PILIER_4, FaSectionType.BOUND, "4.2 Sûretés"),
    CONCLUSION_ARTICULATION(FaPilier.CONCLUSION, FaSectionType.BOUND, "Articulation du financement"),
    CONCLUSION_GARANTIES(FaPilier.CONCLUSION, FaSectionType.BOUND, "Garanties"),
    CONCLUSION_CONDITIONS_BANQUE(FaPilier.CONCLUSION, FaSectionType.BOUND, "Conditions de banque"),

    // The PV's points forts/faibles are BOUND to these two — captured once by
    // the analyst on the FA, never re-typed by the DCM when drafting the PV
    // (real-document analysis, 2026-07-13).
    CONCLUSION_POINTS_FORTS(FaPilier.CONCLUSION, FaSectionType.NARRATIVE, "Points forts"),
    CONCLUSION_POINTS_FAIBLES(FaPilier.CONCLUSION, FaSectionType.NARRATIVE, "Points faibles"),
}
