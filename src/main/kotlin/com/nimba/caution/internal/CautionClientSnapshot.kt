package com.nimba.caution.internal

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/** Frozen mirror of [com.nimba.client.ClientInfo] at caution finalization. */
@Embeddable
data class CautionClientSnapshot(
    @Column(name = "snap_matricule")
    val matricule: String? = null,
    @Column(name = "snap_raison_sociale")
    val raisonSociale: String? = null,
    @Column(name = "snap_sigle")
    val sigle: String? = null,
    @Column(name = "snap_adresse_physique")
    val adressePhysique: String? = null,
    @Column(name = "snap_rccm")
    val rccm: String? = null,
    @Column(name = "snap_account_number")
    val accountNumber: String? = null,
    @Column(name = "snap_agence")
    val agence: String? = null,
)
