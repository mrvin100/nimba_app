package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.OrganizationLogo
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.LineSpacingRule
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowAlign
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyles
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTabJc
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Builds the Word (.docx) document of a case's active trades — the lettres de change
 * (traités) — matching the bank's reference layout (NIMBA-27): each traité is a
 * bordered box holding the logo and organisation name, the tireur block, the payment
 * order, the amount in a small bordered box (currency | figures) followed by the
 * amount in words, then the tiré block and the acceptance line. Three traités are
 * laid out per A4 page (compact Candara type, keep-together rows) so a 25-échéance
 * schedule prints on nine pages ready to cut. Everything is rendered strictly from
 * the generated [Trade] (due date, amount in figures and words, currency), the case
 * (the tiré / lessee), the bank-side constants ([TraiteProperties]) and the
 * organisation identity (name + logo from the identity module).
 */
@Service
class TradeDocxExportService(
    private val trades: TradeRepository,
    private val schedules: AmortizationScheduleRepository,
    private val creditCases: CreditCaseModuleApi,
    private val identity: IdentityModuleApi,
    private val traite: TraiteProperties,
    private val clock: Clock,
) {
    private val dueDateFormat = DateTimeFormatter.ofPattern("dd-MM-uuuu")
    private val monthNames =
        listOf(
            "Janvier",
            "Février",
            "Mars",
            "Avril",
            "Mai",
            "Juin",
            "Juillet",
            "Août",
            "Septembre",
            "Octobre",
            "Novembre",
            "Décembre",
        )

    @Transactional(readOnly = true)
    fun export(
        creditCaseId: UUID,
        signatureDate: LocalDate? = null,
    ): TradeExport {
        val case = creditCases.getOrThrow(creditCaseId)
        val active = trades.findByCreditCaseIdAndActiveIsTrueOrderByDueDateAsc(creditCaseId)
        if (active.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun trade actif à exporter pour ce dossier.")
        }
        val version = schedules.findById(active.first().scheduleId).orElse(null)?.versionNumber ?: 0
        // The acceptance line carries the signature date: the day the document is
        // produced by default, or the date the analyst chose at download time. The
        // lettre de change's own maturity is the due date already printed in the
        // payment order.
        val issueDate = signatureDate ?: LocalDate.now(clock)
        val logo = identity.organizationLogo()

        // The traité prints the client's account when it is captured on the case;
        // the configured bank-side default only covers legacy cases.
        val accountNumber = case.accountNumber?.takeIf { it.isNotBlank() } ?: traite.accountNumber

        val document = XWPFDocument()
        configureA4(document)
        configureTypography(document)
        active.forEachIndexed { index, trade ->
            renderTraite(document, trade, case.clientName, case.currency, accountNumber, issueDate, logo)
            if (index < active.size - 1) {
                // Word merges adjacent tables, so a separator paragraph is mandatory
                // between two traités; it also carries the page break closing each
                // group of TRAITES_PER_PAGE so a printed A4 page holds exactly three.
                separator(document, pageBreak = index % TRAITES_PER_PAGE == TRAITES_PER_PAGE - 1)
            }
        }

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return TradeExport("traites-${case.caseNumber}-v$version.docx", bytes)
    }

    /** A4 portrait with margins sized so three bordered traités fit per page. */
    private fun configureA4(document: XWPFDocument) {
        val body = document.document.body
        val sectPr = if (body.isSetSectPr) body.sectPr else body.addNewSectPr()
        sectPr.addNewPgSz().apply {
            w = BigInteger.valueOf(A4_WIDTH_TWIPS)
            h = BigInteger.valueOf(A4_HEIGHT_TWIPS)
        }
        sectPr.addNewPgMar().apply {
            top = BigInteger.valueOf(MARGIN_VERTICAL_TWIPS)
            bottom = BigInteger.valueOf(MARGIN_VERTICAL_TWIPS)
            left = BigInteger.valueOf(MARGIN_HORIZONTAL_TWIPS)
            right = BigInteger.valueOf(MARGIN_HORIZONTAL_TWIPS)
        }
    }

    /**
     * Document-wide default run properties. Critical for the vertical budget: the
     * PARAGRAPH MARK ending every paragraph renders with the document defaults —
     * Calibri 12 when none are declared — and a 12 pt mark stretches every line
     * to ~15 pt however small the visible runs are, which alone pushes the third
     * traité off the page. Declaring the traité's own type as the default keeps
     * each line at the height actually printed.
     */
    private fun configureTypography(document: XWPFDocument) {
        val ctStyles = CTStyles.Factory.newInstance()
        val runDefaults = ctStyles.addNewDocDefaults().addNewRPrDefault().addNewRPr()
        runDefaults.addNewRFonts().apply {
            ascii = DEFAULT_FONT
            hAnsi = DEFAULT_FONT
            cs = DEFAULT_FONT
        }
        runDefaults.addNewSz().`val` = BigInteger.valueOf(FONT_SIZE_PT * 2L)
        runDefaults.addNewSzCs().`val` = BigInteger.valueOf(FONT_SIZE_PT * 2L)
        document.createStyles().setStyles(ctStyles)
    }

    /** One traité = one bordered single-cell table that never splits across pages. */
    private fun renderTraite(
        document: XWPFDocument,
        trade: Trade,
        lessee: String,
        currency: String,
        accountNumber: String,
        issueDate: LocalDate,
        logo: OrganizationLogo?,
    ) {
        val table = document.createTable(1, 1)
        table.setWidth(CONTENT_WIDTH_TWIPS)
        borders(table)
        table.setCellMargins(CELL_MARGIN_V_TWIPS, CELL_MARGIN_H_TWIPS, CELL_MARGIN_V_TWIPS, CELL_MARGIN_H_TWIPS)
        table.getRow(0).isCantSplitRow = true
        val cell = table.getRow(0).getCell(0)

        header(cell, logo)
        blank(cell)
        labelLine(cell, "Tireur", traite.tireur, valueBold = true)
        labelLine(cell, "Genre d'activité", traite.genreActivite, valueBold = true)
        blank(cell)

        val order = paragraph(cell).also { it.alignment = ParagraphAlignment.BOTH }
        run(order, "Veuillez payer contre cette ")
        run(order, "Lettre de Change", bold = true)
        run(order, " au ")
        run(order, trade.dueDate.format(dueDateFormat), bold = true)
        run(order, " à l'ordre d'")
        run(order, traite.orderBeneficiary, bold = true)

        run(paragraph(cell), "La somme de", italic = true)
        val words = amountBox(cell, currency, grouped(trade.amount))
        // The converted amount already carries the currency wording ("… Francs
        // Guinéens"); it must never be suffixed again here.
        run(words, "= ${trade.amountInWords}", bold = true, italic = true)
        run(
            paragraph(cell),
            "Équivalant à la valeur représentative du crédit leasing à vous octroyer par la banque.",
            italic = true,
        )
        blank(cell)

        labelLine(cell, "Tiré", lessee, valueBold = false)
        labelLine(cell, "Domiciliation", traite.domiciliation, valueBold = true)
        labelLine(cell, "N° de compte", accountNumber, valueBold = false, valueFont = ACCOUNT_FONT)
        labelLine(cell, "Devise", currency, valueBold = true)
        blank(cell)

        val acceptance = paragraph(cell)
        acceptance.indentationLeft = ACCEPTANCE_INDENT_TWIPS
        addTabStop(acceptance, STTabJc.RIGHT, ACCEPTANCE_TAB_TWIPS)
        run(acceptance, "POUR ACCEPTATION :", bold = true).addTab()
        run(acceptance, "${traite.place}, le ${longDate(issueDate)}", bold = true)

        // Free space under the acceptance line so both parties can sign on paper.
        signatureSpace(cell)
    }

    /** Blank area (~1 cm) reserved for the handwritten signatures. */
    private fun signatureSpace(cell: XWPFTableCell) {
        repeat(2) {
            run(paragraph(cell), " ", size = SIGNATURE_LINE_FONT_SIZE_PT)
        }
    }

    /** The organisation logo alone at the head of the traité (the logo already carries the brand). */
    private fun header(
        cell: XWPFTableCell,
        logo: OrganizationLogo?,
    ) {
        val paragraph = cell.paragraphs.first().also { it.spacingAfter = 0 }
        logo?.let { renderLogo(paragraph, it) }
    }

    /** Embeds the logo at a fixed height, preserving its aspect ratio. */
    private fun renderLogo(
        paragraph: XWPFParagraph,
        logo: OrganizationLogo,
    ) {
        try {
            val image = ImageIO.read(ByteArrayInputStream(logo.bytes)) ?: return
            val height = LOGO_HEIGHT_PX
            val width = (LOGO_HEIGHT_PX.toDouble() * image.width / image.height).toInt().coerceAtLeast(1)
            ByteArrayInputStream(logo.bytes).use { stream ->
                paragraph.createRun().addPicture(
                    stream,
                    pictureType(logo.contentType),
                    "organization-logo",
                    Units.pixelToEMU(width),
                    Units.pixelToEMU(height),
                )
            }
        } catch (_: Exception) {
            // A logo that cannot be decoded/embedded must not break the traité export.
        }
    }

    /**
     * The small bordered amount box (currency | figures), right-aligned like the
     * reference. Returns the paragraph that follows the box, which the caller fills
     * with the amount in words.
     */
    private fun amountBox(
        cell: XWPFTableCell,
        currency: String,
        amount: String,
    ): XWPFParagraph {
        val anchor = paragraph(cell)
        val cursor = anchor.ctp.newCursor()
        val box =
            try {
                cell.insertNewTbl(cursor)
            } finally {
                cursor.close()
            }
        box.tableAlignment = TableRowAlign.RIGHT
        borders(box)
        // Unlike createTable, insertNewTbl yields a table without any row.
        val row = box.createRow()
        val currencyCell = row.createCell()
        val amountCell = row.createCell()
        currencyCell.setWidth(CURRENCY_CELL_TWIPS.toString())
        amountCell.setWidth(AMOUNT_CELL_TWIPS.toString())
        val currencyParagraph = currencyCell.paragraphs.first().also { it.alignment = ParagraphAlignment.CENTER }
        run(currencyParagraph, currency, bold = true)
        run(amountCell.paragraphs.first(), " $amount", bold = true)
        return anchor
    }

    private fun borders(table: XWPFTable) {
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, BORDER_EIGHTHS_PT, 0, BORDER_COLOR)
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, BORDER_EIGHTHS_PT, 0, BORDER_COLOR)
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, BORDER_EIGHTHS_PT, 0, BORDER_COLOR)
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, BORDER_EIGHTHS_PT, 0, BORDER_COLOR)
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, BORDER_EIGHTHS_PT, 0, BORDER_COLOR)
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, BORDER_EIGHTHS_PT, 0, BORDER_COLOR)
    }

    /** Label line with the colon aligned on a fixed tab stop, like the reference. */
    private fun labelLine(
        cell: XWPFTableCell,
        label: String,
        value: String,
        valueBold: Boolean,
        valueFont: String = DEFAULT_FONT,
    ) {
        val paragraph = paragraph(cell)
        addTabStop(paragraph, STTabJc.LEFT, LABEL_TAB_TWIPS)
        run(paragraph, label).addTab()
        run(paragraph, ": ")
        run(paragraph, value, bold = valueBold, font = valueFont)
    }

    private fun paragraph(cell: XWPFTableCell): XWPFParagraph = cell.addParagraph().also { it.spacingAfter = 0 }

    private fun blank(cell: XWPFTableCell) {
        val paragraph = paragraph(cell)
        // A thin visual gap needs an EXACT line rule: left to auto, the paragraph
        // mark dictates a full text line for what should be a few points.
        paragraph.setSpacingBetween(BLANK_LINE_EXACT_PT, LineSpacingRule.EXACT)
        run(paragraph, " ", size = BLANK_FONT_SIZE_PT)
    }

    /** Compact paragraph between two traités; closes the page after each group of three. */
    private fun separator(
        document: XWPFDocument,
        pageBreak: Boolean,
    ) {
        val paragraph = document.createParagraph().also { it.spacingAfter = 0 }
        paragraph.setSpacingBetween(BLANK_LINE_EXACT_PT, LineSpacingRule.EXACT)
        val separatorRun = paragraph.createRun().apply { fontSize = BLANK_FONT_SIZE_PT }
        if (pageBreak) separatorRun.addBreak(BreakType.PAGE)
    }

    private fun addTabStop(
        paragraph: XWPFParagraph,
        alignment: STTabJc.Enum,
        positionTwips: Long,
    ) {
        val ppr = paragraph.ctp.pPr ?: paragraph.ctp.addNewPPr()
        val tabs = if (ppr.isSetTabs) ppr.tabs else ppr.addNewTabs()
        tabs.addNewTab().apply {
            `val` = alignment
            pos = BigInteger.valueOf(positionTwips)
        }
    }

    /** Adds a styled run in the traité's typography (compact Candara by default). */
    private fun run(
        paragraph: XWPFParagraph,
        value: String,
        bold: Boolean = false,
        italic: Boolean = false,
        font: String = DEFAULT_FONT,
        size: Int = FONT_SIZE_PT,
    ): XWPFRun =
        paragraph.createRun().apply {
            fontFamily = font
            fontSize = size
            isBold = bold
            isItalic = italic
            setText(value)
        }

    private fun pictureType(contentType: String): Int =
        when (contentType.lowercase()) {
            "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG
            "image/gif" -> Document.PICTURE_TYPE_GIF
            "image/bmp" -> Document.PICTURE_TYPE_BMP
            else -> Document.PICTURE_TYPE_PNG
        }

    private fun grouped(amount: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).format(amount.toBigInteger())
    }

    private fun longDate(date: LocalDate): String = "%02d %s %d".format(date.dayOfMonth, monthNames[date.monthValue - 1], date.year)

    private companion object {
        const val DEFAULT_FONT = "Candara"
        const val ACCOUNT_FONT = "Tahoma"

        /**
         * Vertical budget: the usable A4 height (16838 − 2×400 twips ≈ 800 pt)
         * divided by three leaves ~265 pt per traité. The sizes below keep a full
         * box (worst case: order and amount-in-words wrapping on two lines) around
         * 245 pt, so three ALWAYS fit before each explicit page break — with any
         * slack Word would push the third box and print 2 + 1 alternating pages.
         */
        const val FONT_SIZE_PT = 9
        const val BLANK_FONT_SIZE_PT = 4

        /** Exact height of the thin gap paragraphs (blank lines, separators). */
        const val BLANK_LINE_EXACT_PT = 5.0

        /** Two lines of this size ≈ 0.9 cm of signature room per traité. */
        const val SIGNATURE_LINE_FONT_SIZE_PT = 10
        const val LOGO_HEIGHT_PX = 26
        const val TRAITES_PER_PAGE = 3

        /** A4 portrait geometry, in twips (1/20 pt). */
        const val A4_WIDTH_TWIPS = 11906L
        const val A4_HEIGHT_TWIPS = 16838L
        const val MARGIN_VERTICAL_TWIPS = 400L
        const val MARGIN_HORIZONTAL_TWIPS = 720L
        const val CONTENT_WIDTH_TWIPS = (A4_WIDTH_TWIPS - 2 * MARGIN_HORIZONTAL_TWIPS).toInt()

        /** Inner box geometry. */
        const val CELL_MARGIN_V_TWIPS = 40
        const val CELL_MARGIN_H_TWIPS = 170
        const val LABEL_TAB_TWIPS = 2000L
        const val ACCEPTANCE_INDENT_TWIPS = 720
        const val ACCEPTANCE_TAB_TWIPS = 9600L
        const val CURRENCY_CELL_TWIPS = 900
        const val AMOUNT_CELL_TWIPS = 2600
        const val BORDER_EIGHTHS_PT = 8
        const val BORDER_COLOR = "000000"
    }
}
