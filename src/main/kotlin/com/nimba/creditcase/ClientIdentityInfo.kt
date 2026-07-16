package com.nimba.creditcase

import java.time.LocalDate

/**
 * Descriptive information about the client, captured once on the dossier and
 * reused verbatim on the Fiche d'analyse, the PV, and the FMP — never re-entered
 * per document. All fields are optional: this is supplementary detail the DRI adds
 * incrementally while constituting the dossier, not required to create it.
 * [agence], [gestionnaire] and [analyste] are free text — this bank's directorates
 * and system users are the only identities Nimba itself models; these three name a
 * person or place on paper, not a Nimba account.
 */
data class ClientIdentityInfo(
    val formeJuridique: String? = null,
    val dateCreation: LocalDate? = null,
    val adressePhysique: String? = null,
    val activiteDeBase: String? = null,
    val codeNif: String? = null,
    val principalDirigeant: String? = null,
    val dateEntreeRelation: LocalDate? = null,
    val dateDerniereVisite: LocalDate? = null,
    val agence: String? = null,
    val gestionnaire: String? = null,
    val analyste: String? = null,
    val cotationPrecedente: String? = null,
    val cotationActuelle: String? = null,
)
