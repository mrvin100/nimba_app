package com.nimba.caution

import java.time.Instant
import java.util.UUID

/**
 * Read-only view of a caution dossier, safe to share across module boundaries.
 * [content] carries the shared market context (bénéficiaire, référence de
 * l'appel d'offres, objet, lots, montants par lot) reused across every document
 * of the dossier and its companions, keyed by [CautionFieldDefinition.key].
 */
data class CautionDossierInfo(
    val id: UUID,
    val clientId: UUID,
    val referenceNumber: String,
    val status: DossierStatus,
    val content: Map<String, String>,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedAt: Instant,
)
