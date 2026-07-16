package com.nimba.pv

/**
 * One row of the "débats du comité" table — préoccupation / réponse du
 * gestionnaire / recommandation. Typed by the DCM when drafting the PV (the
 * débats happen in the physical meeting; nothing is captured live during the
 * comité's review in this app).
 */
data class PvDebat(
    val preoccupation: String,
    val reponse: String,
    val recommandation: String,
)
