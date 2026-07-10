package com.nimba.creditcase.internal

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

/** JPA-mapped mirror of [com.nimba.creditcase.ClientIdentityInfo]; see its KDoc. */
@Embeddable
data class ClientIdentity(
    @Column(name = "forme_juridique")
    var formeJuridique: String? = null,
    @Column(name = "date_creation")
    var dateCreation: LocalDate? = null,
    @Column(name = "adresse_physique")
    var adressePhysique: String? = null,
    @Column(name = "activite_de_base")
    var activiteDeBase: String? = null,
    @Column(name = "code_nif")
    var codeNif: String? = null,
    @Column(name = "principal_dirigeant")
    var principalDirigeant: String? = null,
    @Column(name = "date_entree_relation")
    var dateEntreeRelation: LocalDate? = null,
    @Column(name = "date_derniere_visite")
    var dateDerniereVisite: LocalDate? = null,
    @Column(name = "agence")
    var agence: String? = null,
    @Column(name = "gestionnaire")
    var gestionnaire: String? = null,
    @Column(name = "analyste")
    var analyste: String? = null,
    @Column(name = "cotation_precedente")
    var cotationPrecedente: String? = null,
    @Column(name = "cotation_actuelle")
    var cotationActuelle: String? = null,
)
