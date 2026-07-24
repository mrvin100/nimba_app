package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionModuleApi
import com.nimba.caution.CautionStatus
import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.OrganizationSignatories
import com.nimba.shared.toFrenchWords
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** One exported caution document, ready to stream back as a download. */
data class CautionExport(
    val filename: String,
    val content: ByteArray,
)

/**
 * Builds the Word (.docx) export of a finalized caution as an exact replica
 * of the bank's real documents (ground truth: `CAUTION.docx` and `ATTESTATION
 * DE CAPACITE FINANCIERE.docx` in docs/caution): A4, the templates' margins,
 * Tahoma justified body — same conventions confirmed against both files
 * (Tahoma, 11pt default, no header/footer/logo — printed on the bank's own
 * letterhead paper, like the FA/PV/FMP). Only a FINAL caution has a client
 * snapshot to print, mirroring the PV export's own gate.
 */
@Service
class CautionDocxExportService(
    private val cautions: CautionModuleApi,
    private val identity: IdentityModuleApi,
) {
    private val shortDate = DateTimeFormatter.ofPattern("dd-MM-uu")

    @Transactional(readOnly = true)
    fun export(id: UUID): CautionExport {
        val caution =
            cautions.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable")
        if (caution.status != CautionStatus.FINAL) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La caution doit être finalisée avant d'être exportée")
        }
        val snapshot = requireNotNull(caution.clientSnapshot) { "A FINAL caution always carries a client snapshot" }
        val signatories = identity.organizationSignatories()

        val document = XWPFDocument()
        setUpPage(document)
        repeat(4) { spacer(document) }
        when (caution.documentType) {
            CautionDocumentType.SMS -> renderSms(document, caution, snapshot.raisonSociale.orRas(), snapshot.agence, signatories)
            CautionDocumentType.ACF -> renderAcf(document, caution, snapshot, signatories)
        }

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return CautionExport("caution-${caution.referenceNumber}.docx", bytes)
    }

    // ---- Caution de Soumission (SMS) ---------------------------------------------------

    private fun renderSms(
        document: XWPFDocument,
        caution: CautionInfo,
        raisonSociale: String,
        agence: String?,
        signatories: OrganizationSignatories,
    ) {
        val c = caution.content
        titleCentered(document, "CAUTION DE SOUMISSION")
        titleCentered(document, "N° ${caution.referenceNumber}")
        spacer(document)

        paragraph(document, "AFRILAND FIRST BANK ; AGENCE ${agence.orRas()}")
        paragraph(document, "BENEFICIAIRE : ${c["beneficiaire"].orRas()}")
        spacer(document)
        paragraph(document, "DATE : ${fmtShort(c["dateEmission"])}")
        paragraph(document, "GARANTIE N° ${caution.referenceNumber}")
        spacer(document)

        paragraph(
            document,
            "Nous avons été informés que la société $raisonSociale (Ci-après dénommée « le Candidat ») a répondu à " +
                "votre appel d'offres ${c["referenceAppelOffres"].orRas()} relatif aux : ${c["objetMarche"].orRas()} " +
                "Et vous a soumis son offre en date du ${fmtLong(c["dateOffre"])} (ci-après dénommée « l'offre »).",
        )
        paragraph(
            document,
            "En vertu des dispositions du dossier d'Appel d'offres, l'Offre doit être accompagnée d'une garantie d'offre.",
        )
        paragraph(
            document,
            "A la demande du Maître d'ouvrage, nous Afriland First Bank Guinée S.A., Société Anonyme au Capital de " +
                "GNF 200 000 000 000, dont le Siège Social est à Almamya- Commune de Kaloum, B.P. : 343, Conakry - " +
                "République de Guinée, inscrite sur la liste des banques et établissements financiers sous le numéro " +
                "021 et immatriculée au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro " +
                "GC KAL/040.445A/2012 du 17 Mai 2012, représentée par ${signatoryLine(signatories)} dûment habilités, " +
                "ci-après dénommée « la Banque » ;",
        )
        paragraph(
            document,
            "Nous engageons par la présente, sans réserve et irrévocablement, à vous payer à première demande, toute " +
                "somme d'argent que vous pourriez réclamer dans la limite de ${amountLine(c["montant"])}.",
        )
        paragraph(
            document,
            "Votre demande en paiement doit être accompagnée d'une déclaration attestant que le Soumissionnaire n'a " +
                "pas exécuté une des obligations auxquelles il est tenu en vertu de l'Offre à savoir :",
        )
        paragraph(
            document,
            "S'il retire l'Offre pendant la période de validité qu'il a spécifiée dans la lettre de soumission de " +
                "l'offre ; ou pendant toute prolongation de la période de validité de l'offre qu'il aura effectuée ; ou",
        )
        paragraph(
            document,
            "si, s'étant vu notifier l'acceptation de l'Offre par le maître de l'ouvrage pendant la période de " +
                "validité telle qu'indiquée dans la lettre de soumission de l'offre ou prorogée par le maître de " +
                "l'ouvrage avant l'expiration de cette période, il (i) ne signe pas l'acte d'engagement du Marché ; " +
                "ou (ii) il ne fournit pas la garantie de bonne réalisation du Marché et, s'il est tenu de le faire " +
                "ne fournit pas la garantie de performance environnementale, sociale, hygiène et sécurité (ESHS), " +
                "ainsi qu'il est prévu dans les instructions aux soumissionnaires.",
        )
        paragraph(
            document,
            "La présente garantie expire (a) si le marché est octroyé au Soumissionnaire, lorsque nous recevrons une " +
                "copie du Marché signé et de la garantie de bonne exécution émise, et si cela est exigé, la garantie " +
                "de performance environnementale, sociale, hygiène et sécurité (ESHS) émise en votre nom, selon les " +
                "instructions du Soumissionnaire ; ou (b) si le marché n'est pas octroyé au Soumissionnaire, à la " +
                "première des dates suivantes (i) vingt-huit (28) après l'expiration de l'offre ou (c) trois ans " +
                "après la date d'émission de la présente garantie.",
        )
        paragraph(
            document,
            "Toute demande de paiement au titre de la présente garantie doit être reçue à cette date au plus tard " +
                "le ${fmtLong(c["dateExpiration"])}.",
        )
        paragraph(
            document,
            "La présente garantie est régie par les règles uniformes de la chambre de commerce Internationale (CCI) " +
                "relatives aux garanties sur demande, Publication CCI N°758.",
        )
        spacer(document)
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        run(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}")
        spacer(document)
        renderSignatureBlock(document, signatories)
    }

    // ---- Attestation de Capacité Financière (ACF) --------------------------------------

    private fun renderAcf(
        document: XWPFDocument,
        caution: CautionInfo,
        snapshot: CautionClientSnapshotInfo,
        signatories: OrganizationSignatories,
    ) {
        val c = caution.content
        titleCentered(document, "ATTESTATION DE CAPACITE FINANCIERE")
        titleCentered(document, "N° ${caution.referenceNumber}")
        spacer(document)

        paragraph(document, "Adressée à ${c["beneficiaire"].orRas()}")
        spacer(document)
        paragraph(document, "N/Référence : N°${caution.referenceNumber}")
        paragraph(document, "V/Référence : ${c["referenceAppelOffres"].orRas()}")
        paragraph(document, c["objetMarche"].orRas())
        spacer(document)

        val sigle = snapshot.sigle?.takeIf { it.isNotBlank() }?.let { " en abrégé « $it »" } ?: ""
        paragraph(
            document,
            "Nous soussignés, Afriland First Bank Guinée S.A., Société Anonyme au Capital de GNF 200 000 000 000, " +
                "dont le Siège Social est à Almamya - Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC KAL/040.445A/2012 du 17 " +
                "Mai 2012, représentée par ${signatoryLine(signatories)}, en vertu des pouvoirs dont ils sont investis.",
        )
        paragraph(
            document,
            "Certifions par la présente que la société ${snapshot.raisonSociale}$sigle, siège social " +
                "${snapshot.adressePhysique.orRas()}, enregistrée au RCCM ${snapshot.rccm.orRas()} est titulaire du " +
                "compte N°${snapshot.accountNumber.orRas()} ouvert dans nos livres à l'Agence ${snapshot.agence.orRas()}.",
        )
        paragraph(
            document,
            "L'Entreprise dispose à notre connaissance les moyens financiers de ${amountLine(c["montant"])} " +
                "nécessaires à la réalisation du marché pour lequel elle présente une offre.",
        )
        spacer(document)
        paragraph(document, "Fait pour servir et valoir ce que de droit.")
        val faitLe = document.createParagraph()
        run(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}")
        spacer(document)
        renderSignatureBlock(document, signatories)
    }

    // ---- Shared rendering helpers -------------------------------------------------------

    private fun signatoryLine(signatories: OrganizationSignatories): String {
        val first = "Monsieur ${signatories.signataire1Nom.orRas()}, ${signatories.signataire1Titre.orRas()}"
        val second = "Madame ${signatories.signataire2Nom.orRas()}, ${signatories.signataire2Titre.orRas()}"
        return "$first et $second"
    }

    private fun renderSignatureBlock(
        document: XWPFDocument,
        signatories: OrganizationSignatories,
    ) {
        val table = document.createTable(2, 2)
        table.setWidth("100%")
        borderless(table)
        setCell(table.getRow(0).getCell(0), signatories.signataire1Titre.orRas(), bold = true)
        setCell(table.getRow(0).getCell(1), signatories.signataire2Titre.orRas(), bold = true)
        table.getRow(1).getCell(0).addParagraph()
        table.getRow(1).getCell(1).addParagraph()
        setCell(table.getRow(1).getCell(0), signatories.signataire1Nom.orRas())
        setCell(table.getRow(1).getCell(1), signatories.signataire2Nom.orRas())
    }

    private fun amountLine(rawAmount: String?): String {
        val amount = rawAmount?.toBigDecimalOrNull() ?: return "GNF RAS"
        return "GNF ${grouped(amount)} (${amount.toFrenchWords()} Francs Guinéens)"
    }

    // ---- POI helpers (same conventions as the FA/PV/FMP exports) -----------------------

    private fun setUpPage(document: XWPFDocument) {
        val sectPr = document.document.body.addNewSectPr()
        val pageSize = sectPr.addNewPgSz()
        pageSize.w = BigInteger.valueOf(11906)
        pageSize.h = BigInteger.valueOf(16838)
        val margins = sectPr.addNewPgMar()
        margins.left = BigInteger.valueOf(1417)
        margins.right = BigInteger.valueOf(1133)
        margins.top = BigInteger.valueOf(1417)
        margins.bottom = BigInteger.valueOf(1417)
    }

    private fun run(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        size: Int = BODY_SIZE,
    ) {
        val run = paragraph.createRun()
        run.fontFamily = FONT
        run.fontSize = size
        run.isBold = bold
        run.setText(text)
    }

    private fun paragraph(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingAfter = 160
        run(p, text)
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph()
    }

    private fun titleCentered(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.CENTER
        run(p, text, bold = true, size = TITLE_SIZE)
    }

    private fun setCell(
        cell: XWPFTableCell,
        text: String,
        bold: Boolean = false,
    ) {
        val p = cell.paragraphs.first()
        run(p, text, bold = bold)
    }

    private fun borderless(table: XWPFTable) {
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
    }

    // ---- Value formatting -------------------------------------------------------------

    private fun String?.orRas(): String = this?.takeIf { it.isNotBlank() } ?: "RAS"

    private fun fmtShort(value: String?): String = value?.let { runCatching { LocalDate.parse(it).format(shortDate) }.getOrNull() } ?: "RAS"

    private fun fmtLong(value: String?): String {
        val date = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "RAS"
        val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRENCH))
        return formatted.split(" ").joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun grouped(amount: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).format(amount.toBigInteger())
    }

    private companion object {
        const val FONT = "Tahoma"
        const val BODY_SIZE = 11
        const val TITLE_SIZE = 14
    }
}
