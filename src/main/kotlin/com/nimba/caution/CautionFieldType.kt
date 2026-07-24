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
 * Where a field's value lives once the dossier is the aggregate root. COMMON
 * fields are entered once on the dossier and inherited by every document
 * (bénéficiaire, marché, signataires, montant/devise…); SPECIFIC fields are
 * proper to a single document (a lot, an expiry date, a reference to an
 * original caution…). This is the source of truth for "ne jamais demander deux
 * fois la même information": the frontend asks COMMON fields on the dossier and
 * only SPECIFIC fields on a document, and [CautionFieldRegistry.effectiveContent]
 * merges them at read time.
 */
enum class CautionFieldScope {
    COMMON,
    SPECIFIC,
}

/**
 * One field of a document type's form, exposed as metadata so the frontend
 * never hardcodes a per-type page. [optional] fields (e.g. a signatory's
 * civility) may be left blank: they are not required to create or finalize a
 * document, and simply do not print when unset. [scope] says whether the field
 * belongs to the dossier (COMMON) or to a single document (SPECIFIC).
 */
data class CautionFieldDefinition(
    val key: String,
    val label: String,
    val type: CautionFieldType,
    val optional: Boolean = false,
    val scope: CautionFieldScope = CautionFieldScope.SPECIFIC,
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
    /**
     * The common fields — the market context and the signatories — entered once
     * on the dossier and inherited by every document. All carry
     * [CautionFieldScope.COMMON]. The amount and currency are NOT here: they are
     * proper to each document (a multi-lot request has a different amount per
     * lot), see [PER_DOCUMENT_FIELDS].
     */
    val SHARED_FIELDS =
        listOf(
            CautionFieldDefinition("beneficiaire", "Bénéficiaire (Maître d'ouvrage)", CautionFieldType.TEXT),
            CautionFieldDefinition("referenceAppelOffres", "Référence de l'appel d'offres", CautionFieldType.TEXT),
            CautionFieldDefinition("objetMarche", "Objet du marché", CautionFieldType.TEXT),
            CautionFieldDefinition("dateEmission", "Date d'émission", CautionFieldType.DATE),
            CautionFieldDefinition("signataire1Civilite", "Signataire 1 — Civilité", CautionFieldType.CIVILITY, optional = true),
            CautionFieldDefinition("signataire1Nom", "Signataire 1 — Nom complet", CautionFieldType.TEXT),
            CautionFieldDefinition("signataire1Titre", "Signataire 1 — Titre", CautionFieldType.TEXT),
            CautionFieldDefinition("signataire2Civilite", "Signataire 2 — Civilité", CautionFieldType.CIVILITY, optional = true),
            CautionFieldDefinition("signataire2Nom", "Signataire 2 — Nom complet", CautionFieldType.TEXT),
            CautionFieldDefinition("signataire2Titre", "Signataire 2 — Titre", CautionFieldType.TEXT),
        ).map { it.copy(scope = CautionFieldScope.COMMON) }

    /** Per-document fields common to every type but proper to each document (the amount and its currency differ from one lot to the next). */
    private val PER_DOCUMENT_FIELDS =
        listOf(
            CautionFieldDefinition("devise", "Devise", CautionFieldType.CURRENCY),
            CautionFieldDefinition("montant", "Montant", CautionFieldType.AMOUNT),
        ).map { it.copy(scope = CautionFieldScope.SPECIFIC) }

    private val SPECIFIC_FIELDS: Map<CautionDocumentType, List<CautionFieldDefinition>> =
        mapOf(
            CautionDocumentType.SMS to
                listOf(
                    CautionFieldDefinition("dateOffre", "Date de l'offre", CautionFieldType.DATE),
                    CautionFieldDefinition("dateExpiration", "Date d'expiration de la garantie", CautionFieldType.DATE),
                ),
            CautionDocumentType.ACF to emptyList(),
            CautionDocumentType.AFC to emptyList(),
            CautionDocumentType.PRO to
                listOf(
                    CautionFieldDefinition("cautionOrigineReference", "Référence de la caution d'origine", CautionFieldType.TEXT),
                    CautionFieldDefinition("cautionOrigineDate", "Date d'émission de la caution d'origine", CautionFieldType.DATE),
                    CautionFieldDefinition("nouvelleDateExpiration", "Nouvelle date d'expiration", CautionFieldType.DATE),
                ),
        )

    /** A type's specific fields: the per-document amount/currency, then the type's own fields. */
    fun specificFieldsFor(type: CautionDocumentType): List<CautionFieldDefinition> = PER_DOCUMENT_FIELDS + SPECIFIC_FIELDS.getValue(type)

    fun allFieldsFor(type: CautionDocumentType): List<CautionFieldDefinition> = SHARED_FIELDS + specificFieldsFor(type)

    /** The common (dossier-level) fields, inherited by every document. */
    fun commonFields(): List<CautionFieldDefinition> = SHARED_FIELDS

    /**
     * A document's effective content: the dossier's [common] values, overridden
     * by the document's own [specific] answers. This is the inheritance rule —
     * a document never re-stores a common field, it reads it from its dossier,
     * yet may still override one when a document genuinely differs. A blank
     * override does not erase the inherited value.
     */
    fun effectiveContent(
        common: Map<String, String>,
        specific: Map<String, String>,
    ): Map<String, String> = common + specific.filterValues { it.isNotBlank() }
}
