package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionModuleApi
import com.nimba.client.ClientModuleApi
import com.nimba.client.getOrThrow
import com.nimba.shared.amountInWords
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowAlign
import org.apache.poi.xwpf.usermodel.TableWidthType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd
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
 * Builds the Word (.docx) export of a caution as an exact replica of the
 * bank's real documents (ground truth: `CAUTION.docx` and `ATTESTATION DE
 * CAPACITE FINANCIERE.docx` in docs/caution): A4, the templates' margins,
 * Tahoma justified body, the double-bordered/shaded header box, and every
 * bold run matching the reference exactly. A FINAL caution prints its frozen
 * client snapshot; a DRAFT is exportable too, as a preview, rendered from the
 * live client record so the DCM can check the document before finalizing.
 * Signatories are the caution's own content fields (not a bank-wide setting):
 * a signatory can differ from one document to the next (delegation), so each
 * document keeps its own answer once finalized, same as every other entered
 * field.
 */
@Service
class CautionDocxExportService(
    private val cautions: CautionModuleApi,
    private val clients: ClientModuleApi,
) {
    private val shortDate = DateTimeFormatter.ofPattern("dd-MM-uu")

    @Transactional(readOnly = true)
    fun export(id: UUID): CautionExport {
        val caution =
            cautions.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable")
        // A finalized caution carries its frozen snapshot; a draft preview reads the client live.
        val snapshot = caution.clientSnapshot ?: liveSnapshot(caution.clientId)

        val document = XWPFDocument()
        setUpPage(document)
        when (caution.documentType) {
            CautionDocumentType.SMS -> renderSms(document, caution, snapshot.raisonSociale.orRas(), snapshot.agence)
            CautionDocumentType.ACF -> renderAcf(document, caution, snapshot)
            CautionDocumentType.AFC -> renderAfc(document, caution, snapshot)
        }

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        val suffix = if (caution.clientSnapshot == null) "-brouillon" else ""
        return CautionExport("caution-${caution.referenceNumber}$suffix.docx", bytes)
    }

    /** The live client record projected onto the same shape as the frozen snapshot, for draft previews. */
    private fun liveSnapshot(clientId: UUID): CautionClientSnapshotInfo {
        val client = clients.getOrThrow(clientId)
        return CautionClientSnapshotInfo(
            matricule = client.matricule,
            raisonSociale = client.raisonSociale,
            sigle = client.sigle,
            adressePhysique = client.adressePhysique,
            rccm = client.rccm,
            accountNumber = client.accountNumber,
            agence = client.agence,
        )
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
            bold(signatoryLabel(c, 1)),
            plain(" et "),
            bold(signatoryLabel(c, 2)),
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
            bold("${amountClause(c)}."),
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

    /**
     * Renders the Attestation de Capacité Financière as a faithful replica of
     * `ATTESTATION DE CAPACITE FINANCIERE.docx`: a page-centered header box at
     * the model's width, the two reference lines fully bold, and the body with
     * exactly the model's bold runs — the company name, its acronym and its
     * account number are bold; its address, RCCM and agency are not (matching
     * the reference). The amount is bold as a whole. Civility prefixes
     * (Monsieur/Madame) are intentionally dropped, as on the caution, so an
     * unset signatory never prints a gendered title.
     */
    private fun renderAcf(
        document: XWPFDocument,
        caution: CautionInfo,
        snapshot: CautionClientSnapshotInfo,
    ) {
        val c = caution.content
        headerBox(
            document,
            "ATTESTATION DE CAPACITE FINANCIERE",
            caution.referenceNumber,
            widthDxa = ACF_HEADER_WIDTH,
            centered = true,
        )
        spacer(document)
        spacer(document)

        mixedParagraph(
            document,
            plain("Adressée à "),
            bold(c["beneficiaire"].orRas()),
            alignment = ParagraphAlignment.CENTER,
        )
        spacer(document)
        mixedParagraph(document, bold("N/Référence : N°${caution.referenceNumber}"), alignment = ParagraphAlignment.LEFT)
        mixedParagraph(document, bold("V/Référence : ${c["referenceAppelOffres"].orRas()}"))
        mixedParagraph(document, bold(c["objetMarche"].orRas()))
        spacer(document)

        mixedParagraph(
            document,
            plain("Nous soussignés, "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya - Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                    "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC – KAL/040.445A/2012 du " +
                    "17 Mai 2012, représentée par ",
            ),
            bold(signatoryLabel(c, 1)),
            plain(" et "),
            bold(signatoryLabel(c, 2)),
            plain(", en vertu des pouvoirs dont ils sont investis."),
        )

        val certifySegments =
            buildList {
                add(plain("Certifions par la présente que la société "))
                add(bold(snapshot.raisonSociale.orRas()))
                snapshot.sigle?.takeIf { it.isNotBlank() }?.let {
                    add(plain(" en abrégé « "))
                    add(bold(it))
                    add(plain(" »"))
                }
                add(
                    plain(
                        " siège social ${snapshot.adressePhysique.orRas()}, enregistrée au RCCM/${snapshot.rccm.orRas()} " +
                            "est titulaire du compte N°",
                    ),
                )
                add(bold(snapshot.accountNumber.orRas()))
                add(plain(" ouvert dans nos livres à l'Agence ${snapshot.agence.orRas()}."))
            }
        mixedParagraph(document, *certifySegments.toTypedArray())

        mixedParagraph(
            document,
            plain("L'Entreprise dispose à notre connaissance les moyens financiers de "),
            bold(amountClause(c)),
            plain(" nécessaires à la réalisation du marché pour lequel elle présente une offre."),
        )
        spacer(document)
        paragraph(document, "Fait pour servir et valoir ce que de droit.")
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    // ---- Attestation de Facilité de Crédit (AFC) ---------------------------------------

    /**
     * Renders the Attestation de Facilité de Crédit as a faithful replica of
     * `AFC  LOT8.docx`: a page-centered header box at the model's width, a
     * centered "Adressée à …" line, and the two credit clauses with the model's
     * bold runs — the bank certifies it would grant credit up to the amount, to
     * the company, for the market. The amount prints without the leading
     * currency code (the words carry the currency), matching the model.
     */
    private fun renderAfc(
        document: XWPFDocument,
        caution: CautionInfo,
        snapshot: CautionClientSnapshotInfo,
    ) {
        val c = caution.content
        headerBox(document, "ATTESTATION DE FACILITE DE CREDIT", caution.referenceNumber, widthDxa = AFC_HEADER_WIDTH, centered = true)
        spacer(document)
        spacer(document)

        mixedParagraph(
            document,
            plain("Adressée à "),
            bold(c["beneficiaire"].orRas()),
            alignment = ParagraphAlignment.CENTER,
        )
        spacer(document)

        mixedParagraph(
            document,
            plain("Nous soussignés, "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya - Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                    "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC – KAL/040.445A/2012 du " +
                    "17 Mai 2012, représentée par ",
            ),
            bold(signatoryLabel(c, 1)),
            plain(" et "),
            bold(signatoryLabel(c, 2)),
            plain(" dûment habilités."),
        )
        mixedParagraph(
            document,
            plain("Attestons par la présente que nous serions disposés à consentir nos concours à hauteur de "),
            bold(amountClause(c, withCurrencyCode = false)),
            plain(" à la "),
            bold(snapshot.raisonSociale.orRas()),
            plain(" dans le cadre du Marché de l'appel d'offres National "),
            bold(c["referenceAppelOffres"].orRas()),
            plain(" relatif aux "),
            bold(c["objetMarche"].orRas()),
            plain("."),
        )
        mixedParagraph(
            document,
            plain("Cette attestation est délivrée à la "),
            bold(snapshot.raisonSociale.orRas()),
            plain(", siège social ${snapshot.adressePhysique.orRas()}, immatriculée sous le "),
            bold("N°RCCM/${snapshot.rccm.orRas()}"),
            plain(" pour servir et faire valoir ce que de droit."),
        )
        paragraph(
            document,
            "Ledit accompagnement se fera sous réserve de la validation par notre comité de Crédit Compétent, seul " +
                "organe habilité à statuer en matière de crédit dans notre institution.",
        )
        paragraph(document, "En foi de quoi, la présente certification est établie pour servir et faire valoir ce que de droit.")
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    // ---- Shared rendering helpers -------------------------------------------------------

    private fun signatoryName(
        content: Map<String, String>,
        index: Int,
    ): String = content["signataire${index}Nom"].orRas()

    private fun signatoryTitle(
        content: Map<String, String>,
        index: Int,
    ): String = content["signataire${index}Titre"].orRas()

    /**
     * "Monsieur Nom, Titre" — how a signatory appears inside the body prose. The
     * civility prefix only shows when it was entered (the models carry it); an
     * unset civility is simply omitted rather than printing a placeholder.
     */
    private fun signatoryLabel(
        content: Map<String, String>,
        index: Int,
    ): String {
        val civility = content["signataire${index}Civilite"]?.takeIf { it.isNotBlank() }?.let { "$it " } ?: ""
        return "$civility${signatoryName(content, index)}, ${signatoryTitle(content, index)}"
    }

    /**
     * The closing signature block: a full page-width, borderless two-column
     * table. Row 1 holds the two signatories' titles, row 2 their names, all
     * bold. Signatory 1 is pinned left, signatory 2 right, and a generous gap
     * is left between the two rows so the signatures can be handwritten there.
     */
    private fun renderSignatureBlock(
        document: XWPFDocument,
        content: Map<String, String>,
    ) {
        val table = document.createTable(2, 2)
        table.setWidthType(TableWidthType.DXA)
        table.setWidth(CONTENT_WIDTH)
        borderless(table)
        val half = (CONTENT_WIDTH / 2).toString()
        setCell(table.getRow(0).getCell(0), signatoryTitle(content, 1), bold = true, alignment = ParagraphAlignment.LEFT, width = half)
        setCell(table.getRow(0).getCell(1), signatoryTitle(content, 2), bold = true, alignment = ParagraphAlignment.RIGHT, width = half)
        setCell(
            table.getRow(1).getCell(0),
            signatoryName(content, 1),
            bold = true,
            alignment = ParagraphAlignment.LEFT,
            width = half,
            spacingBefore = SIGNATURE_GAP,
        )
        setCell(
            table.getRow(1).getCell(1),
            signatoryName(content, 2),
            bold = true,
            alignment = ParagraphAlignment.RIGHT,
            width = half,
            spacingBefore = SIGNATURE_GAP,
        )
    }

    /**
     * "GNF 238 756 476 (Deux Cent ... Seize Francs Guinéens)" — bold as a whole,
     * since the amount and its currency are entered via the creation form. The
     * raw amount is reduced to its digits first, so a value typed with spaces or
     * thousands separators ("238 756 476") still resolves instead of silently
     * falling back to "RAS". No trailing punctuation: each caller adds its own,
     * since the clause ends a sentence in one document and continues it in another.
     */
    private fun amountClause(
        content: Map<String, String>,
        withCurrencyCode: Boolean = true,
    ): String {
        val digits = content["montant"]?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
        val amount = digits?.toBigDecimal() ?: return "RAS"
        val currency = content["devise"]?.takeIf { it.isNotBlank() } ?: "GNF"
        // The attestation de facilité prints the amount without the leading currency code (the words carry the currency).
        val prefix = if (withCurrencyCode) "$currency " else ""
        return "$prefix${grouped(amount)} (${amount.amountInWords(currency)})"
    }

    // ---- Header box ---------------------------------------------------------------------

    /**
     * The double-bordered, 25%-shaded title box every reference template opens
     * with: a fixed-width rectangle holding the title then the reference number,
     * both bold and centered. Pinned in DXA (with the cell at the same width) so
     * it renders as a clean banner rather than shrinking to the text. The
     * attestation matches its model's narrower, page-centered box; the caution
     * spans the full writable width.
     */
    private fun headerBox(
        document: XWPFDocument,
        title: String,
        referenceNumber: String,
        widthDxa: Int = CONTENT_WIDTH,
        centered: Boolean = false,
    ) {
        val table = document.createTable(1, 1)
        table.setWidthType(TableWidthType.DXA)
        table.setWidth(widthDxa)
        if (centered) table.setTableAlignment(TableRowAlign.CENTER)
        val borderSize = 18
        table.setTopBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setBottomBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setLeftBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setRightBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")

        val cell = table.getRow(0).getCell(0)
        cell.widthType = TableWidthType.DXA
        cell.setWidth(widthDxa.toString())
        cell.verticalAlignment = XWPFTableCell.XWPFVertAlign.CENTER
        applyPct25Shading(cell)
        val titlePara = cell.paragraphs.first()
        titlePara.alignment = ParagraphAlignment.CENTER
        titlePara.spacingBefore = 80
        addRun(titlePara, title, bold = true, size = TITLE_SIZE)
        val refPara = cell.addParagraph()
        refPara.alignment = ParagraphAlignment.CENTER
        refPara.spacingAfter = 80
        addRun(refPara, "N° $referenceNumber", bold = true, size = TITLE_SIZE)
    }

    /** The reference templates fill the header with a 25% pattern shade (w:shd val="pct25"); replicated here rather than approximated with a solid fill. */
    private fun applyPct25Shading(cell: XWPFTableCell) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val shd = if (tcPr.isSetShd) tcPr.shd else tcPr.addNewShd()
        shd.`val` = STShd.PCT_25
        shd.color = "auto"
        shd.fill = "auto"
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

    /** A paragraph built from a sequence of bold/plain runs — how every entered field gets bolded inline with fixed prose. Justified unless a caller overrides it. */
    private fun mixedParagraph(
        document: XWPFDocument,
        vararg segments: Segment,
        alignment: ParagraphAlignment = ParagraphAlignment.BOTH,
    ) {
        val p = document.createParagraph()
        p.alignment = alignment
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
        width: String? = null,
        spacingBefore: Int = 0,
    ) {
        width?.let {
            cell.widthType = TableWidthType.DXA
            cell.setWidth(it)
        }
        val p = cell.paragraphs.first()
        p.alignment = alignment
        if (spacingBefore > 0) p.spacingBefore = spacingBefore
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

        /** Writable page width in twips: A4 (11906) minus the left (1417) and right (1133) margins set in [setUpPage]. */
        const val CONTENT_WIDTH = 9356

        /** The attestation model's header box width in twips (page-centered, narrower than the caution's full-width banner). */
        const val ACF_HEADER_WIDTH = 7896

        /** The attestation de facilité model's header box width in twips (page-centered). */
        const val AFC_HEADER_WIDTH = 6521

        /** Vertical gap (twips, ~45pt) left between a signatory's title and name so the signature can be handwritten between them. */
        const val SIGNATURE_GAP = 900
    }
}
