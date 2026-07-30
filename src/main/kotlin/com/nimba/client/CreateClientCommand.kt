package com.nimba.client

import java.time.LocalDate
import java.util.UUID

/**
 * Opens a client record. [matricule] is the bank's own internal client code —
 * assigned by the bank before Nimba existed for long-standing relationships, so it
 * is entered rather than generated. It is **optional**: a client can be created for
 * a leasing dossier without one and completed later; the Caution module requires it
 * to issue a document. When present it must be unique, and is what a Caution's
 * reference number and cross-document lookup key off.
 */
data class CreateClientCommand(
    val matricule: String? = null,
    val raisonSociale: String,
    val createdBy: UUID,
    val type: ClientType = ClientType.ENTREPRISE,
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
