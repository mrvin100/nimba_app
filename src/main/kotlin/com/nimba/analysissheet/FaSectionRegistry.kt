package com.nimba.analysissheet

import com.nimba.creditcase.FaVariant

/**
 * Which sections apply to a given FA variant, in the exported document's order
 * (docs/nimba-fa-document-spec.md §2). The single source of truth the sections
 * endpoint and its edit gate both read from — a section key not returned here
 * can never be fetched or written for that case.
 */
object FaSectionRegistry {
    private val COVER =
        listOf(
            FaSectionKey.COVER_INFOS_DEMANDE,
            FaSectionKey.COVER_INFOS_INTERNES,
            FaSectionKey.COVER_PROPOSITION,
            FaSectionKey.COVER_CONDITIONS_BANQUE,
            FaSectionKey.COVER_GARANTIES,
        )

    // §1.1 through §1.16 — identical for both leasing variants; SANS_CONTRAT
    // appends §1.17 (the payer synthesis) right after.
    private val PILIER_1 =
        listOf(
            FaSectionKey.PILIER1_INFOS_GENERALES,
            FaSectionKey.PILIER1_REGULARITE,
            FaSectionKey.PILIER1_SIGNATAIRES,
            FaSectionKey.PILIER1_POUVOIRS,
            FaSectionKey.PILIER1_DERNIERE_VISITE,
            FaSectionKey.PILIER1_MOUVEMENTS,
            FaSectionKey.PILIER1_RENTABILITE_COMPTE,
            FaSectionKey.PILIER1_ACTIONNARIAT,
            FaSectionKey.PILIER1_MORALITE,
            FaSectionKey.PILIER1_PERSONNES_CLES,
            FaSectionKey.PILIER1_ORGANIGRAMME,
            FaSectionKey.PILIER1_RELATIONS_BANCAIRES,
            FaSectionKey.PILIER1_LOGISTIQUE,
            FaSectionKey.PILIER1_CLIENTS,
            FaSectionKey.PILIER1_FOURNISSEURS,
            FaSectionKey.PILIER1_FONCTIONNEMENT_ACTIVITE,
            FaSectionKey.PILIER1_CONTRATS_REALISES,
            FaSectionKey.PILIER1_ENGAGEMENTS_NOS_LIVRES,
            FaSectionKey.PILIER1_ENGAGEMENTS_CONFRERES,
            FaSectionKey.PILIER1_CENTRALE_RISQUES,
            FaSectionKey.PILIER1_ENGAGEMENTS_APPARENTES,
            FaSectionKey.PILIER1_BILAN,
            FaSectionKey.PILIER1_COMPTE_RESULTAT,
            FaSectionKey.PILIER1_SYNTHESE,
        )

    // Pilier 2 diverges completely: the contract presentation (UHODA style)
    // versus the market analysis (IKT style); both close on the encaissements.
    private val PILIER_2_AVEC_CONTRAT =
        listOf(
            FaSectionKey.PILIER2_CONTRAT,
            FaSectionKey.PILIER2_CONNAISSANCE_MO,
            FaSectionKey.PILIER2_PLANNING,
            FaSectionKey.PILIER2_ENCAISSEMENTS,
        )

    private val PILIER_2_SANS_CONTRAT =
        listOf(
            FaSectionKey.PILIER2_MARCHE_SECTEUR,
            FaSectionKey.PILIER2_MARCHE_DEMANDE,
            FaSectionKey.PILIER2_MARCHE_OFFRE,
            FaSectionKey.PILIER2_POSITIONNEMENT,
            FaSectionKey.PILIER2_ENCAISSEMENTS,
        )

    private val PILIER_3 =
        listOf(
            FaSectionKey.PILIER3_BESOINS,
            FaSectionKey.PILIER3_JUSTIFICATIFS,
            FaSectionKey.PILIER3_HYPOTHESE_H1,
            FaSectionKey.PILIER3_HYPOTHESE_H2,
            FaSectionKey.PILIER3_HYPOTHESE_CHARGES,
            FaSectionKey.PILIER3_CEP,
            FaSectionKey.PILIER3_DECAISSEMENT,
            FaSectionKey.PILIER3_RENTABILITE_BANQUE,
            FaSectionKey.PILIER3_SIMULATION_FINANCEMENT,
        )

    private val PILIER_4 =
        listOf(
            FaSectionKey.PILIER4_RISQUES,
            FaSectionKey.PILIER4_SURETES,
        )

    private val CONCLUSION =
        listOf(
            FaSectionKey.CONCLUSION_POINTS_FORTS,
            FaSectionKey.CONCLUSION_POINTS_FAIBLES,
            FaSectionKey.CONCLUSION_OPPORTUNITES,
            FaSectionKey.CONCLUSION_ARTICULATION,
            FaSectionKey.CONCLUSION_GARANTIES,
            FaSectionKey.CONCLUSION_CONDITIONS_BANQUE,
        )

    private val ANNEXES_SANS_CONTRAT =
        listOf(
            FaSectionKey.ANNEXE_PAYEUR_BILAN,
            FaSectionKey.ANNEXE_PAYEUR_COMPTE_RESULTAT,
            FaSectionKey.ANNEXE_LISTE_CLIENTS,
        )

    private val LEASING_AVEC_CONTRAT_SECTIONS =
        COVER + PILIER_1 + PILIER_2_AVEC_CONTRAT + PILIER_3 + PILIER_4 + CONCLUSION

    private val LEASING_SANS_CONTRAT_SECTIONS =
        COVER + PILIER_1 + FaSectionKey.PILIER1_SYNTHESE_PAYEUR + PILIER_2_SANS_CONTRAT +
            PILIER_3 + PILIER_4 + CONCLUSION + ANNEXES_SANS_CONTRAT

    fun sectionsFor(variant: FaVariant): List<FaSectionKey> =
        when (variant) {
            FaVariant.LEASING_AVEC_CONTRAT -> LEASING_AVEC_CONTRAT_SECTIONS
            FaVariant.LEASING_SANS_CONTRAT -> LEASING_SANS_CONTRAT_SECTIONS
            // MC2/MUFFA's FA structure has not been provided yet (open point in
            // the design doc) — no sections apply until it is.
            FaVariant.MC2_MUFFA -> emptyList()
        }
}
