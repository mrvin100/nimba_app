package com.nimba.analysissheet

/**
 * Default content served for sections that come prefilled instead of empty —
 * today only §4.1's leasing risk matrix, transcribed verbatim from the real
 * documents (UHODA/IKT share it word for word). Returned as
 * [FaSectionInfo.defaultContentJson] so the frontend editor seeds from it and
 * the export prints it when the analyst never touched the section — one source
 * for both, JSON shape identical to what the editor would save.
 */
object FaSectionDefaults {
    // language=json
    private val RISQUES_LEASING =
        """
        {"rows":[
        {"nature":"Risque de crédit","facteurs":"La couverture des engagements","mesures":"L'analyste utilise la méthodologie prescrite par la procédure de crédit « leasing ordinaire » pour mitiger les risques de crédit. Il s'agit de la méthode des piliers.\nLa domiciliation des paiements du contrat\nLes nantissements du contrat et de l'équipement existant\nL'assurance tous risques\nTracking sur les engins propriété de la banque"},
        {"nature":"Risques résiduels","facteurs":"Mauvaise performance de l'équipement","mesures":"L'expérience du client\nProcessus d'agrément des fournisseurs\nGaranties complémentaires\nAjuster le niveau du premier loyer/dépôt de garantie"},
        {"nature":"Risques résiduels","facteurs":"Équipement peu fiable","mesures":"Privilégier les marques connues pour leurs performances\nProcessus d'agrément des fournisseurs\nGaranties complémentaires\nAjuster le niveau du premier loyer/dépôt de garantie"},
        {"nature":"Risques résiduels","facteurs":"Pièces de rechange non disponibles","mesures":"Demander un contrat de maintenance avec le fournisseur\nPrivilégier les marques connues pour la disponibilité des pièces de rechange"},
        {"nature":"Risques résiduels","facteurs":"Pas de service après-vente (maintenance)","mesures":"Contrat de maintenance sur la période de location\nGaranties complémentaires\nRelever le niveau du dépôt de garantie/premier loyer"},
        {"nature":"Risques résiduels","facteurs":"Absence d'un marché secondaire","mesures":"Évaluer le marché secondaire\nPrivilégier les équipements bénéficiant d'un marché secondaire\nGarantie complémentaire\nAjuster le niveau du premier loyer/dépôt de garantie"},
        {"nature":"Risques de marché","facteurs":"Caractère volatile des prix sur le marché secondaire","mesures":"Évaluer le marché secondaire\nPrivilégier les équipements standards"}
        ]}
        """.trimIndent()

    fun defaultContentFor(key: FaSectionKey): String? =
        when (key) {
            FaSectionKey.PILIER4_RISQUES -> RISQUES_LEASING
            else -> null
        }
}
