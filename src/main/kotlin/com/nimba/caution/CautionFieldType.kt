package com.nimba.caution

/** Drives which input widget the frontend's dynamic form renders for a field. */
enum class CautionFieldType {
    TEXT,
    DATE,
    AMOUNT,
}

/** One field of a document type's form, exposed as metadata so the frontend never hardcodes a per-type page. */
data class CautionFieldDefinition(
    val key: String,
    val label: String,
    val type: CautionFieldType,
)

/**
 * The field list per document type — shared fields are asked once even when
 * several documents are requested together; specific fields only apply to
 * their own type. This IS the "generic document engine" metadata: adding a
 * future document type means adding an entry here, not a new hardcoded form.
 */
object CautionFieldRegistry {
    val SHARED_FIELDS =
        listOf(
            CautionFieldDefinition("beneficiaire", "Bénéficiaire (Maître d'ouvrage)", CautionFieldType.TEXT),
            CautionFieldDefinition("referenceAppelOffres", "Référence de l'appel d'offres", CautionFieldType.TEXT),
            CautionFieldDefinition("objetMarche", "Objet du marché", CautionFieldType.TEXT),
            CautionFieldDefinition("montant", "Montant", CautionFieldType.AMOUNT),
            CautionFieldDefinition("dateEmission", "Date d'émission", CautionFieldType.DATE),
        )

    private val SPECIFIC_FIELDS: Map<CautionDocumentType, List<CautionFieldDefinition>> =
        mapOf(
            CautionDocumentType.SMS to
                listOf(
                    CautionFieldDefinition("dateOffre", "Date de l'offre", CautionFieldType.DATE),
                    CautionFieldDefinition("dateExpiration", "Date d'expiration de la garantie", CautionFieldType.DATE),
                ),
            CautionDocumentType.ACF to emptyList(),
        )

    fun specificFieldsFor(type: CautionDocumentType): List<CautionFieldDefinition> = SPECIFIC_FIELDS.getValue(type)

    fun allFieldsFor(type: CautionDocumentType): List<CautionFieldDefinition> = SHARED_FIELDS + specificFieldsFor(type)
}
