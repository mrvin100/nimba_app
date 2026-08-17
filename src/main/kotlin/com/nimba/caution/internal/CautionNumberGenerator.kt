package com.nimba.caution.internal

import com.nimba.caution.CautionDocumentType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Formats a caution document's reference number,
 * `{sequence}-{matricule}-{documentType}-{date}` (e.g. `04370-038044-SMS-11-02-26`),
 * and a dossier's, `DOS-{date}-{sequence}` (e.g. `DOS-11-02-26-04370`). A
 * document embeds the issuing client's matricule (it identifies the paper
 * trail alongside the client); a dossier does not, since it is purely an
 * internal grouping, not a document handed to anyone outside the bank.
 *
 * Unlike a plain auto-increment, [sequence] is never assigned here: SMS, ACF,
 * AFC and the dossier each run their own independent series, and the DCM
 * analyst enters the number themselves (locked once finalized, same as the
 * rest of the document) rather than the platform silently picking one. This
 * class only formats the string and suggests the next free value per series
 * ([suggestNextSequence]/[suggestNextDossierSequence]) — the actual value used
 * comes from the create/update request, validated for uniqueness within its
 * series by the database constraint (`uq_caution_type_sequence`,
 * `uq_caution_dossier_sequence`).
 */
@Component
class CautionNumberGenerator(
    private val documents: CautionDocumentRepository,
    private val dossiers: CautionDossierRepository,
    private val clock: Clock,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-uu")

    fun referenceNumber(
        sequence: Int,
        matricule: String,
        documentType: CautionDocumentType,
    ): String {
        val date = LocalDate.now(clock).format(dateFormat)
        return "%05d-%s-%s-%s".format(sequence, matricule, documentType.code, date)
    }

    fun dossierReferenceNumber(sequence: Int): String {
        val date = LocalDate.now(clock).format(dateFormat)
        return "%s-%s-%05d".format(DOSSIER_CODE, date, sequence)
    }

    /** A document reference with only its leading sequence segment swapped, so an edit never disturbs the matricule/type/date it was issued with. */
    fun withSequence(
        referenceNumber: String,
        sequence: Int,
    ): String = "%05d-%s".format(sequence, referenceNumber.substringAfter('-'))

    /** The next free number in [documentType]'s own series — a suggestion only, the analyst may enter any other free value. */
    @Transactional(readOnly = true)
    fun suggestNextSequence(documentType: CautionDocumentType): Int = (documents.maxSequence(documentType) ?: 0) + 1

    /** The next free number in the dossier series. */
    @Transactional(readOnly = true)
    fun suggestNextDossierSequence(): Int = (dossiers.maxSequence() ?: 0) + 1

    private companion object {
        const val DOSSIER_CODE = "DOS"
    }
}
