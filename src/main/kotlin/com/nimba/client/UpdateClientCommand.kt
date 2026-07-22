package com.nimba.client

import java.time.LocalDate

/** Replaces a client's descriptive details wholesale; [matricule] itself never changes after creation. */
data class UpdateClientCommand(
    val raisonSociale: String,
    val sigle: String? = null,
    val formeJuridique: String? = null,
    val dateCreation: LocalDate? = null,
    val adressePhysique: String? = null,
    val activiteDeBase: String? = null,
    val codeNif: String? = null,
    val rccm: String? = null,
    val accountNumber: String? = null,
    val principalDirigeant: String? = null,
    val dateEntreeRelation: LocalDate? = null,
    val dateDerniereVisite: LocalDate? = null,
    val agence: String? = null,
    val gestionnaire: String? = null,
    val analyste: String? = null,
    val cotationPrecedente: String? = null,
    val cotationActuelle: String? = null,
)
