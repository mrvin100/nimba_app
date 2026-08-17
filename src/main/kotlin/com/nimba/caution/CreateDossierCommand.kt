package com.nimba.caution

import java.util.UUID

/**
 * Opens a caution dossier in OPEN status and assigns its reference number
 * immediately. [content] carries the shared market context (bénéficiaire,
 * référence de l'appel d'offres, objet, lots, montants par lot) that the
 * dossier's documents and companions all reuse. [sequence] is the
 * analyst-entered number in the dossier's own series (DOS), independent of
 * any document type's.
 */
data class CreateDossierCommand(
    val clientId: UUID,
    val content: Map<String, String>,
    val createdBy: UUID,
    val sequence: Int,
)
