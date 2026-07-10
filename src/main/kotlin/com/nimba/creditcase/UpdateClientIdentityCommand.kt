package com.nimba.creditcase

import java.time.LocalDate

/** Request to replace a case's client-identity details (see [ClientIdentityInfo]). */
data class UpdateClientIdentityCommand(
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
