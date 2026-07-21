package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionModuleApi
import com.nimba.caution.CautionStatus
import com.nimba.shared.amountInWords
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowAlign
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

/** One run of text within a paragraph, bold or not — every value the DCM entered via the creation form is printed bold, exactly like the bank marks up its own paper templates. */
private data class Segment(
    val text: String,
    val bold: Boolean = false,
)

private fun plain(text: String) = Segment(text)

private fun bold(text: String) = Segment(text, bold = true)

/**
 * Builds the Word (.docx) export of a finalized caution as an exact replica
 * of the bank's real documents (ground truth: `CAUTION.docx` and `ATTESTATION
 * DE CAPACITE FINANCIERE.docx` in docs/caution): A4, the templates' margins,
 * Tahoma justified body, the double-bordered/shaded header box, and every
 * bold run matching the reference exactly. Only a FINAL caution has a client
 * snapshot to print, mirroring the PV export's own gate. Signatories are the
 * caution's own content fields (not a bank-wide setting) — a signatory can
 * differ from one document to the next (delegation), so each document keeps
 * its own answer once finalized, same as every other entered field.
 */
@Service
class CautionDocxExportService(
    private val cautions: CautionModuleApi,
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

        val document = XWPFDocument()
        setUpPage(document)
        repeat(4) { spacer(document) }
        when (caution.documentType) {
            CautionDocumentType.SMS -> renderSms(document, caution, snapshot.raisonSociale.orRas(), snapshot.agence)
            CautionDocumentType.ACF -> renderAcf(document, caution, snapshot)
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
    ) {
        val c = caution.content
        headerBox(document, "CAUTION DE SOUMISSION", caution.referenceNumber)
        spacer(document)

        boldCenteredLine(document, "AFRILAND FIRST BANK ; AGENCE ${agence.orRas()}")
        boldCenteredLine(document, "BENEFICIAIRE : ${c["beneficiaire"].orRas()}")
        spacer(document)
        boldCenteredLine(document, "DATE : ${fmtShort(c["dateEmission"])}")
        boldCenteredLine(document, "GARANTIE N° ${caution.referenceNumber}")
        spacer(document)

        mixedParagraph(
            document,
            plain("Nous avons été informés que la société "),
            bold(raisonSociale),
            plain(" (Ci-après dénommée "),
            bold("« le Candidat »"),
            plain(") a répondu à votre appel d'offres National Restreint "),
            bold(c["referenceAppelOffres"].orRas()),
            plain(" relatif aux : "),
            bold(c["objetMarche"].orRas()),
            plain(" Et vous a soumis son offre en date du "),
            bold(fmtLong(c["dateOffre"])),
            plain(" (ci-après dénommée « "),
            bold("l'offre"),
            plain(" »)."),
        )
        paragraph(
            document,
            "En vertu des dispositions du dossier d'Appel d'offres, l'Offre doit être accompagnée d'une garantie d'offre.",
        )
        mixedParagraph(
            document,
            plain("A la demande du Maître d'ouvrage, nous "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya-Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                    "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC–KAL/040.445A/2012 du " +
                    "17 Mai 2012, représentée par ",
            ),
            bold("${signatoryName(c, 1)}, ${signatoryTitle(c, 1)}"),
            plain(" et "),
            bold("${signatoryName(c, 2)}, ${signatoryTitre2(c)}"),
            plain(" dûment habilités, ci-après dénommée "),
            bold("« la Banque »"),
            plain(" ;"),
        )
        mixedParagraph(
            document,
            plain(
                "Nous engageons par la présente, sans réserve et irrévocablement, à vous payer à première demande, " +
                    "toute somme d'argent que vous pourriez réclamer dans la limite de ",
            ),
            bold(amountClause(c)),
        )
        paragraph(
            document,
            "Votre demande en paiement doit être accompagnée d'une déclaration attestant que le Soumissionnaire n'a " +
                "pas exécuté une des obligations auxquelles il est tenu en vertu de l'Offre à savoir :",
        )
        listItem(
            document,
            "a)",
            "S'il retire l'Offre pendant la période de validité qu'il a spécifiée dans la lettre de soumission de " +
                "l'offre ; ou pendant toute prolongation de la période de validité de l'offre qu'il aura effectuée ; ou",
        )
        listItem(
            document,
            "b)",
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
        mixedParagraph(
            document,
            plain("Toute demande de paiement au titre de la présente garantie doit être reçue à cette date au plus tard le "),
            bold("${fmtLong(c["dateExpiration"])}."),
        )
        paragraph(
            document,
            "La présente garantie est régie par les règles uniformes de la chambre de commerce Internationale (CCI) " +
                "relatives aux garanties sur demande, Publication CCI N°758.",
        )
        spacer(document)
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    // ---- Attestation de Capacité Financière (ACF) --------------------------------------

    private fun renderAcf(
        document: XWPFDocument,
        caution: CautionInfo,
        snapshot: CautionClientSnapshotInfo,
    ) {
        val c = caution.content
        headerBox(document, "ATTESTATION DE CAPACITE FINANCIERE", caution.referenceNumber)
        spacer(document)

        paragraph(document, "Adressée à ${c["beneficiaire"].orRas()}")
        spacer(document)
        paragraph(document, "N/Référence : N°${caution.referenceNumber}")
        paragraph(document, "V/Référence : ${c["referenceAppelOffres"].orRas()}")
        paragraph(document, c["objetMarche"].orRas())
        spacer(document)

        val sigle = snapshot.sigle?.takeIf { it.isNotBlank() }?.let { " en abrégé « $it »" } ?: ""
        mixedParagraph(
            document,
            plain("Nous soussignés, "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya-Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                    "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC–KAL/040.445A/2012 du " +
                    "17 Mai 2012, représentée par ",
            ),
            bold("${signatoryName(c, 1)}, ${signatoryTitle(c, 1)}"),
            plain(" et "),
            bold("${signatoryName(c, 2)}, ${signatoryTitre2(c)}"),
            plain(", en vertu des pouvoirs dont ils sont investis."),
        )
        mixedParagraph(
            document,
            plain("Certifions par la présente que la société "),
            bold("${raisonSocialeOf(snapshot)}$sigle"),
            plain(", siège social "),
            bold(snapshot.adressePhysique.orRas()),
            plain(", enregistrée au RCCM "),
            bold(snapshot.rccm.orRas()),
            plain(" est titulaire du compte N°"),
            bold(snapshot.accountNumber.orRas()),
            plain(" ouvert dans nos livres à l'Agence "),
            bold(snapshot.agence.orRas()),
            plain("."),
        )
        mixedParagraph(
            document,
            plain("L'Entreprise dispose à notre connaissance les moyens financiers de "),
            bold(amountClause(c)),
            plain(" nécessaires à la réalisation du marché pour lequel elle présente une offre."),
        )
        spacer(document)
        paragraph(document, "Fait pour servir et valoir ce que de droit.")
        val faitLe = document.createParagraph()
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    private fun raisonSocialeOf(snapshot: CautionClientSnapshotInfo): String = snapshot.raisonSociale

    // ---- Shared rendering helpers -------------------------------------------------------

    private fun signatoryName(
        content: Map<String, String>,
        index: Int,
    ): String = content["signataire${index}Nom"].orRas()

    private fun signatoryTitle(
        content: Map<String, String>,
        index: Int,
    ): String = content["signataire${index}Titre"].orRas()

    /** Kept as its own function only so the mixed-paragraph call sites read symmetrically; identical to [signatoryTitle]. */
    private fun signatoryTitre2(content: Map<String, String>): String = signatoryTitle(content, 2)

    private fun renderSignatureBlock(
        document: XWPFDocument,
        content: Map<String, String>,
    ) {
        val table = document.createTable(2, 2)
        table.setWidth(100)
        borderless(table)
        setCell(table.getRow(0).getCell(0), signatoryTitle(content, 1), bold = true, alignment = ParagraphAlignment.LEFT)
        setCell(table.getRow(0).getCell(1), signatoryTitle(content, 2), bold = true, alignment = ParagraphAlignment.RIGHT)
        table.getRow(1).getCell(0).addParagraph()
        table.getRow(1).getCell(1).addParagraph()
        setCell(table.getRow(1).getCell(0), signatoryName(content, 1), alignment = ParagraphAlignment.LEFT)
        setCell(table.getRow(1).getCell(1), signatoryName(content, 2), alignment = ParagraphAlignment.RIGHT)
    }

    /** "GNF 238 756 476 (Deux Cent ... Seize Francs Guinéens)" — bold as a whole, since the amount and its currency are entered via the creation form. */
    private fun amountClause(content: Map<String, String>): String {
        val amount = content["montant"]?.toBigDecimalOrNull() ?: return "RAS"
        val currency = content["devise"]?.takeIf { it.isNotBlank() } ?: "GNF"
        return "$currency ${grouped(amount)} (${amount.amountInWords(currency)})."
    }

    // ---- Header box ---------------------------------------------------------------------

    /** The double-bordered, 25%-shaded title box every reference template opens with — title then reference number, both bold and centered. */
    private fun headerBox(
        document: XWPFDocument,
        title: String,
        referenceNumber: String,
    ) {
        val table = document.createTable(1, 1)
        table.setWidth(70)
        table.setTableAlignment(TableRowAlign.CENTER)
        val borderSize = 18
        table.setTopBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setBottomBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setLeftBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setRightBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")

        val cell = table.getRow(0).getCell(0)
        cell.setColor("D9D9D9")
        val titlePara = cell.paragraphs.first()
        titlePara.alignment = ParagraphAlignment.CENTER
        addRun(titlePara, title, bold = true, size = TITLE_SIZE)
        val refPara = cell.addParagraph()
        refPara.alignment = ParagraphAlignment.CENTER
        addRun(refPara, "N° $referenceNumber", bold = true, size = TITLE_SIZE)
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

    private fun addRun(
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
        addRun(p, text)
    }

    /** A justified paragraph built from a sequence of bold/plain runs — how every entered field gets bolded inline with fixed prose. */
    private fun mixedParagraph(
        document: XWPFDocument,
        vararg segments: Segment,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingAfter = 160
        segments.forEach { seg -> addRun(p, seg.text, bold = seg.bold) }
    }

    /** "a) ..." / "b) ..." — the offer-withdrawal clauses print as a lettered list, not plain paragraphs. */
    private fun listItem(
        document: XWPFDocument,
        marker: String,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingAfter = 160
        p.indentationLeft = 360
        p.indentationHanging = 360
        addRun(p, "$marker $text")
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph()
    }

    /** The header's identifying lines (agence, bénéficiaire, date, référence) — bold and centered, unlike the justified body. */
    private fun boldCenteredLine(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.CENTER
        addRun(p, text, bold = true)
    }

    private fun setCell(
        cell: XWPFTableCell,
        text: String,
        bold: Boolean = false,
        alignment: ParagraphAlignment = ParagraphAlignment.LEFT,
    ) {
        val p = cell.paragraphs.first()
        p.alignment = alignment
        addRun(p, text, bold = bold)
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
