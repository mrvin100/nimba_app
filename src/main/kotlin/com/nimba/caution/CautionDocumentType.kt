package com.nimba.caution

/**
 * Every kind of caution/attestation the DCM can generate. Adding a future
 * type (AVD - Avance sur Démarrage, or another) means adding a new constant
 * here, its field list in `CautionFieldRegistry`, and its own docx renderer —
 * never touching an existing type's code (design locked 2026-07-21).
 */
enum class CautionDocumentType(
    val code: String,
    val label: String,
) {
    SMS("SMS", "Caution de Soumission"),
    ACF("ACF", "Attestation de Capacité Financière"),
}
