package com.nimba.pv.internal

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.LocalDate

/** Frozen mirror of [com.nimba.creditcase.ClientIdentityInfo] at PV finalization. */
@Embeddable
data class PvIdentitySnapshot(
    @Column(name = "snap_forme_juridique")
    val formeJuridique: String? = null,
    @Column(name = "snap_date_creation")
    val dateCreation: LocalDate? = null,
    @Column(name = "snap_adresse_physique")
    val adressePhysique: String? = null,
    @Column(name = "snap_activite_de_base")
    val activiteDeBase: String? = null,
    @Column(name = "snap_code_nif")
    val codeNif: String? = null,
    @Column(name = "snap_principal_dirigeant")
    val principalDirigeant: String? = null,
    @Column(name = "snap_date_entree_relation")
    val dateEntreeRelation: LocalDate? = null,
    @Column(name = "snap_date_derniere_visite")
    val dateDerniereVisite: LocalDate? = null,
    @Column(name = "snap_agence")
    val agence: String? = null,
    @Column(name = "snap_gestionnaire")
    val gestionnaire: String? = null,
    @Column(name = "snap_analyste")
    val analyste: String? = null,
    @Column(name = "snap_cotation_precedente")
    val cotationPrecedente: String? = null,
    @Column(name = "snap_cotation_actuelle")
    val cotationActuelle: String? = null,
)
