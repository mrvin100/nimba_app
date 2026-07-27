package com.nimba.client.internal

import com.nimba.client.ClientType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A client of the bank, independent of any credit case — introduced for the
 * Caution module (SMS, ACF, later AVD...), whose documents must be grouped
 * reliably by [matricule] even for a company that never has a leasing
 * dossier. [matricule] is the bank's own internal client code, not generated
 * by Nimba (see [com.nimba.client.CreateClientCommand]'s KDoc).
 *
 * Deliberately NOT unified with `CreditCase.clientIdentity` (a separate
 * embeddable on the credit-case module) — merging the two would be a
 * cross-cutting migration of a system already in production. A client who
 * has both a credit case and cautions currently has their identity captured
 * in both places.
 */
@Entity
@Table(name = "client")
class Client(
    /**
     * The bank's internal client code. Optional: a client created from a migrated
     * leasing dossier may not have one yet (it is captured later), while the Caution
     * module requires it to issue a document. Unique when present (a partial unique
     * index, so several matricule-less clients can coexist).
     */
    @Column(name = "matricule", updatable = false)
    val matricule: String? = null,
    @Column(name = "raison_sociale", nullable = false)
    var raisonSociale: String,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    var type: ClientType = ClientType.ENTREPRISE

    @Column(name = "sigle")
    var sigle: String? = null

    @Column(name = "forme_juridique")
    var formeJuridique: String? = null

    @Column(name = "date_creation")
    var dateCreation: LocalDate? = null

    @Column(name = "adresse_physique")
    var adressePhysique: String? = null

    @Column(name = "activite_de_base")
    var activiteDeBase: String? = null

    @Column(name = "code_nif")
    var codeNif: String? = null

    @Column(name = "rccm")
    var rccm: String? = null

    @Column(name = "account_number")
    var accountNumber: String? = null

    @Column(name = "principal_dirigeant")
    var principalDirigeant: String? = null

    @Column(name = "date_entree_relation")
    var dateEntreeRelation: LocalDate? = null

    @Column(name = "date_derniere_visite")
    var dateDerniereVisite: LocalDate? = null

    @Column(name = "agence")
    var agence: String? = null

    @Column(name = "gestionnaire")
    var gestionnaire: String? = null

    @Column(name = "analyste")
    var analyste: String? = null

    @Column(name = "cotation_precedente")
    var cotationPrecedente: String? = null

    @Column(name = "cotation_actuelle")
    var cotationActuelle: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
