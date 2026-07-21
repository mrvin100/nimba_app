package com.nimba.analysissheet

/**
 * One section of the Fiche d'analyse — the full structure of both LEASING
 * variants per docs/nimba-fa-document-spec.md §3–§4, analyzed from the real
 * documents (UHODA = AVEC_CONTRAT, IKT = SANS_CONTRAT). Declaration order is
 * the shared document order; [FaSectionRegistry] builds each variant's exact
 * ordered list (which sections apply, and where the variant-specific ones
 * interleave). Enum names are stable storage keys (`analysis_sheet_section.
 * section_key`, VARCHAR(60)) — never rename one that may have stored rows.
 *
 * The per-key content JSON shapes live with their editors in the frontend;
 * [FaSectionType]'s KDoc documents the generic shape per type.
 */
enum class FaSectionKey(
    val pilier: FaPilier,
    val type: FaSectionType,
    val label: String,
) {
    // ---- Couverture -------------------------------------------------------
    COVER_INFOS_DEMANDE(FaPilier.COVER, FaSectionType.KEY_VALUE, "Informations sur la demande"),
    COVER_INFOS_INTERNES(FaPilier.COVER, FaSectionType.KEY_VALUE, "Informations internes"),
    COVER_PROPOSITION(FaPilier.COVER, FaSectionType.NARRATIVE, "Proposition de décision de l'analyste"),
    COVER_CONDITIONS_BANQUE(FaPilier.COVER, FaSectionType.BOUND, "Conditions de banque"),
    COVER_GARANTIES(FaPilier.COVER, FaSectionType.BOUND, "Garanties"),

    // ---- Pilier 1 — Connaissance de l'entreprise --------------------------
    PILIER1_INFOS_GENERALES(FaPilier.PILIER_1, FaSectionType.BOUND, "1.1 Informations générales"),
    PILIER1_REGULARITE(FaPilier.PILIER_1, FaSectionType.BOUND, "1.2 Régularité juridique et informatique dans notre base"),
    PILIER1_SIGNATAIRES(FaPilier.PILIER_1, FaSectionType.TABLE, "1.2.1 Principaux signataires dans le système"),
    PILIER1_POUVOIRS(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.2.2 Pouvoir des signataires"),
    PILIER1_DERNIERE_VISITE(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.3 Dernière visite commerciale des installations"),
    PILIER1_MOUVEMENTS(FaPilier.PILIER_1, FaSectionType.TABLE, "1.4 Fonctionnement du compte dans nos livres"),
    PILIER1_RENTABILITE_COMPTE(FaPilier.PILIER_1, FaSectionType.TABLE, "1.4.1 Rentabilité du compte dans nos livres"),
    PILIER1_ACTIONNARIAT(FaPilier.PILIER_1, FaSectionType.TABLE, "1.5 Structure de l'actionnariat"),
    PILIER1_MORALITE(FaPilier.PILIER_1, FaSectionType.TABLE, "1.5.1 Moralité et honorabilité des principaux actionnaires"),
    PILIER1_PERSONNES_CLES(FaPilier.PILIER_1, FaSectionType.TABLE, "1.6 Présentation des personnes clés de l'entreprise"),
    PILIER1_ORGANIGRAMME(FaPilier.PILIER_1, FaSectionType.IMAGE, "1.7 Organigramme de fonctionnement de l'activité"),
    PILIER1_RELATIONS_BANCAIRES(FaPilier.PILIER_1, FaSectionType.TABLE, "1.8 Les relations bancaires"),
    PILIER1_LOGISTIQUE(FaPilier.PILIER_1, FaSectionType.TABLE, "1.9 Situation de la logistique de l'entreprise"),
    PILIER1_CLIENTS(FaPilier.PILIER_1, FaSectionType.TABLE, "1.10 Situation des clients"),
    PILIER1_FOURNISSEURS(FaPilier.PILIER_1, FaSectionType.TABLE, "1.11 Situation des fournisseurs"),
    PILIER1_FONCTIONNEMENT_ACTIVITE(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.12 Fonctionnement de l'activité de l'entreprise"),
    PILIER1_CONTRATS_REALISES(FaPilier.PILIER_1, FaSectionType.TABLE, "1.13 Situation des contrats déjà réalisés"),
    PILIER1_ENGAGEMENTS_NOS_LIVRES(FaPilier.PILIER_1, FaSectionType.TABLE, "1.14.1.1 Les engagements bancaires dans nos livres"),
    PILIER1_ENGAGEMENTS_CONFRERES(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.14.1.2 Chez les confrères"),
    PILIER1_CENTRALE_RISQUES(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.14.1.3 La centrale des risques"),
    PILIER1_ENGAGEMENTS_APPARENTES(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.14.2 Engagements sur sociétés et personnes apparentées"),
    PILIER1_BILAN(FaPilier.PILIER_1, FaSectionType.FINANCIAL, "1.15.1 Bilan comptable"),
    PILIER1_COMPTE_RESULTAT(FaPilier.PILIER_1, FaSectionType.FINANCIAL, "1.15.2 Compte de résultat"),
    PILIER1_SYNTHESE(FaPilier.PILIER_1, FaSectionType.NARRATIVE, "1.16 Synthèse sur la connaissance de la relation"),

    /** SANS_CONTRAT only — the payer takes the contract's place, so the FA synthesizes him. */
    PILIER1_SYNTHESE_PAYEUR(FaPilier.PILIER_1, FaSectionType.TABLE, "1.17 Synthèse sur la connaissance du payeur"),

    // ---- Pilier 2 — AVEC_CONTRAT: présentation du contrat -----------------
    PILIER2_CONTRAT(FaPilier.PILIER_2, FaSectionType.KEY_VALUE, "2.1 Présentation du contrat à financer"),
    PILIER2_CONNAISSANCE_MO(FaPilier.PILIER_2, FaSectionType.NARRATIVE, "2.2 Connaissance du maître d'ouvrage"),
    PILIER2_PLANNING(FaPilier.PILIER_2, FaSectionType.NARRATIVE, "2.2.1 Planning d'exécution"),

    // ---- Pilier 2 — SANS_CONTRAT: analyse du marché -----------------------
    PILIER2_MARCHE_SECTEUR(FaPilier.PILIER_2, FaSectionType.IMAGE, "2.1 Connaissance générale du secteur"),
    PILIER2_MARCHE_DEMANDE(FaPilier.PILIER_2, FaSectionType.NARRATIVE, "2.1.1 Analyse de la demande"),
    PILIER2_MARCHE_OFFRE(FaPilier.PILIER_2, FaSectionType.FLEX_TABLE, "2.1.2 Analyse de l'offre"),
    PILIER2_POSITIONNEMENT(FaPilier.PILIER_2, FaSectionType.NARRATIVE, "2.1.3 Positionnement du payeur et du client"),

    // ---- Pilier 2 — shared ------------------------------------------------
    PILIER2_ENCAISSEMENTS(FaPilier.PILIER_2, FaSectionType.TABLE, "2.3 Historique des encaissements du contrat"),

    // ---- Pilier 3 — Analyse financière ------------------------------------
    PILIER3_BESOINS(FaPilier.PILIER_3, FaSectionType.IMAGE, "3.1.1 Besoins exprimés"),
    PILIER3_JUSTIFICATIFS(FaPilier.PILIER_3, FaSectionType.FLEX_TABLE, "3.1.2 Justificatifs des besoins exprimés"),
    PILIER3_HYPOTHESE_H1(FaPilier.PILIER_3, FaSectionType.FLEX_TABLE, "3.2.1 Hypothèse H1 : Optimiste"),
    PILIER3_HYPOTHESE_H2(FaPilier.PILIER_3, FaSectionType.FLEX_TABLE, "3.2.2 Hypothèse H2 : Pessimiste"),
    PILIER3_HYPOTHESE_CHARGES(FaPilier.PILIER_3, FaSectionType.FLEX_TABLE, "3.2.3 Hypothèse des charges"),
    PILIER3_CEP(FaPilier.PILIER_3, FaSectionType.TABLE, "3.2.4 Compte d'exploitation prévisionnel"),
    PILIER3_DECAISSEMENT(FaPilier.PILIER_3, FaSectionType.NARRATIVE, "3.3 Plan de décaissement et plan de trésorerie prévisionnel"),
    PILIER3_RENTABILITE_BANQUE(FaPilier.PILIER_3, FaSectionType.COMPUTED, "3.4 Rentabilité pour la banque"),
    PILIER3_SIMULATION_FINANCEMENT(FaPilier.PILIER_3, FaSectionType.COMPUTED, "3.5 Simulation du financement"),

    // ---- 4. Synthèse des risques et présentation des sûretés --------------
    PILIER4_RISQUES(FaPilier.PILIER_4, FaSectionType.TABLE, "4.1 Analyse des risques"),
    PILIER4_SURETES(FaPilier.PILIER_4, FaSectionType.BOUND, "4.2 Présentation des sûretés"),

    // ---- Conclusion -------------------------------------------------------
    // The PV's points forts/faibles are BOUND to these two — captured once by
    // the analyst on the FA, never re-typed by the DCM when drafting the PV
    // (real-document analysis, 2026-07-13).
    CONCLUSION_POINTS_FORTS(FaPilier.CONCLUSION, FaSectionType.NARRATIVE, "Points forts"),
    CONCLUSION_POINTS_FAIBLES(FaPilier.CONCLUSION, FaSectionType.NARRATIVE, "Points faibles"),
    CONCLUSION_OPPORTUNITES(FaPilier.CONCLUSION, FaSectionType.NARRATIVE, "Opportunités à saisir"),
    CONCLUSION_ARTICULATION(FaPilier.CONCLUSION, FaSectionType.BOUND, "Articulation du financement"),
    CONCLUSION_GARANTIES(FaPilier.CONCLUSION, FaSectionType.BOUND, "Garanties"),
    CONCLUSION_CONDITIONS_BANQUE(FaPilier.CONCLUSION, FaSectionType.BOUND, "Conditions de banque"),

    // ---- Annexes — SANS_CONTRAT only --------------------------------------
    ANNEXE_PAYEUR_BILAN(FaPilier.ANNEXES, FaSectionType.FINANCIAL, "Annexe 1.1 Bilan du payeur"),
    ANNEXE_PAYEUR_COMPTE_RESULTAT(FaPilier.ANNEXES, FaSectionType.FINANCIAL, "Annexe 1.2 Compte de résultat du payeur"),
    ANNEXE_LISTE_CLIENTS(FaPilier.ANNEXES, FaSectionType.IMAGE, "Annexe 2 Liste des clients"),
}
