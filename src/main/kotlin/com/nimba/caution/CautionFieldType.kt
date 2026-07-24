package com.nimba.caution

/** Drives which input widget the frontend's dynamic form renders for a field. */
enum class CautionFieldType {
    TEXT,
    DATE,
    AMOUNT,

    /** A currency code (GNF, USD, EUR...) — the frontend renders a select, defaulting to GNF. */
    CURRENCY,

    /** A signatory's civility (Monsieur / Madame) — the frontend renders a select; blank means it is omitted from the document. */
    CIVILITY,
}

/**
 * One field of a document type's form, exposed as metadata so the frontend
 * never hardcodes a per-type page. [optional] fields (e.g. a signatory's
 * civility) may be left blank: they are not required to create or finalize a
 * document, and simply do not print when unset.
 */
data class CautionFieldDefinition(
    val key: String,
    val label: String,
    val type: CautionFieldType,
    val optional: Boolean = false,
)

/**
 * The field list per document type — shared fields are asked once even when
 * several documents are requested together; specific fields only apply to
 * their own type. This IS the "generic document engine" metadata: adding a
 * future document type means adding an entry here, not a new hardcoded form.
 *
 * The two signatories and the montant/devise pair are shared because every
 * generated document needs them, not because they're conceptually part of
 * one specific type — a signatory can differ from one document to the next
 * (delegation), which is why they're entered per document rather than read
 * from a single bank-wide setting.
 */
object CautionFieldRegistry {
    val SHARED_FIELDS =
        listOf(
            CautionFieldDefinition("beneficiaire", "Bénéficiaire (Maître d'ouvrage)", CautionFieldType.TEXT),
            CautionFieldDefinition("referenceAppelOffres", "Référence de l'appel d'offres", CautionFieldType.TEXT),
            CautionFieldDefinition("objetMarche", "Objet du marché", CautionFieldType.TEXT),
            CautionFieldDefinition("devise", "Devise", CautionFieldType.CURRENCY),
            CautionFieldDefinition("montant", "Montant", CautionFieldType.AMOUNT),
            CautionFieldDefinition("dateEmission", "Date d'émission", CautionFieldType.DATE),
            CautionFieldDefinition("signataire1Civilite", "Signataire 1 — Civilité", CautionFieldType.CIVILITY, optional = true),
            CautionFieldDefinition("signataire1Nom", "Signataire 1 — Nom complet", CautionFieldType.TEXT),
            CautionFieldDefinition("signataire1Titre", "Signataire 1 — Titre", CautionFieldType.TEXT),
            CautionFieldDefinition("signataire2Civilite", "Signataire 2 — Civilité", CautionFieldType.CIVILITY, optional = true),
            CautionFieldDefinition("signataire2Nom", "Signataire 2 — Nom complet", CautionFieldType.TEXT),
            CautionFieldDefinition("signataire2Titre", "Signataire 2 — Titre", CautionFieldType.TEXT),
        )

    private val SPECIFIC_FIELDS: Map<CautionDocumentType, List<CautionFieldDefinition>> =
        mapOf(
            CautionDocumentType.SMS to
                listOf(
                    CautionFieldDefinition("dateOffre", "Date de l'offre", CautionFieldType.DATE),
                    CautionFieldDefinition("dateExpiration", "Date d'expiration de la garantie", CautionFieldType.DATE),
                ),
            CautionDocumentType.ACF to emptyList(),
            CautionDocumentType.AFC to emptyList(),
        )

    fun specificFieldsFor(type: CautionDocumentType): List<CautionFieldDefinition> = SPECIFIC_FIELDS.getValue(type)

    fun allFieldsFor(type: CautionDocumentType): List<CautionFieldDefinition> = SHARED_FIELDS + specificFieldsFor(type)
}
