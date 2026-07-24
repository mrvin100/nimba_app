package com.nimba.client

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Read-only view of a client record, safe to share across module boundaries.
 * A client exists independently of any credit case — the Caution module's
 * documents (and, later, any other business line) key off [matricule] to
 * group every generated document for the same company, whether or not it
 * ever has a leasing dossier.
 */
data class ClientInfo(
    val id: UUID,
    val type: ClientType,
    /** The bank's internal client code; null until captured (see [CreateClientCommand]). */
    val matricule: String?,
    val raisonSociale: String,
    val sigle: String?,
    val formeJuridique: String?,
    val dateCreation: LocalDate?,
    val adressePhysique: String?,
    val activiteDeBase: String?,
    val codeNif: String?,
    val rccm: String?,
    val accountNumber: String?,
    val principalDirigeant: String?,
    val dateEntreeRelation: LocalDate?,
    val dateDerniereVisite: LocalDate?,
    val agence: String?,
    val gestionnaire: String?,
    val analyste: String?,
    val cotationPrecedente: String?,
    val cotationActuelle: String?,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)
