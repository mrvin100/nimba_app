package com.nimba.client

/**
 * Corrects a client's matricule after a data-entry mistake. Deliberately a
 * separate action from [UpdateClientCommand] (gated to a direction manager,
 * not any member) — the matricule feeds document numbering and identity
 * matching across every product, so changing it is rarer and more sensitive
 * than editing the rest of the descriptive fiche.
 */
data class UpdateClientMatriculeCommand(
    val matricule: String?,
)
