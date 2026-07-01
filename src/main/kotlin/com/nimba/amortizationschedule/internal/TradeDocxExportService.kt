package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.OrganizationLogo
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFRun
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Builds the Word (.docx) document of a case's active trades — the lettres de change
 * (traités), one traité per échéance in two copies (bank + client), matching the
 * bank's traité layout (NIMBA-27). Each traité is rendered strictly from the
 * generated [Trade] (due date, amount in figures and in words, currency) and the
 * case (the tiré / lessee), plus the bank-side constants ([TraiteProperties]) and the
 * organisation logo (from the identity module), so the document is coherent with the
 * imported schedule end to end and visually matches the reference model: Candara 14pt,
 * the logo at the head of each copy, labels in normal weight with their values in bold.
 */
@Service
class TradeDocxExportService(
    private val trades: TradeRepository,
    private val schedules: AmortizationScheduleRepository,
    private val creditCases: CreditCaseModuleApi,
    private val identity: IdentityModuleApi,
    private val traite: TraiteProperties,
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
    fun export(creditCaseId: UUID): TradeExport {
        val case =
            creditCases.findById(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")
        val active = trades.findByCreditCaseIdAndActiveIsTrueOrderByDueDateAsc(creditCaseId)
        if (active.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun trade actif à exporter pour ce dossier.")
        }
        val version = schedules.findById(active.first().scheduleId).orElse(null)?.versionNumber ?: 0
        val issueDate = active.first().dueDate
        val logo = identity.organizationLogo()

        val document = XWPFDocument()
        active.forEachIndexed { index, trade ->
            renderTraite(document, trade, case.clientName, case.currency, issueDate, logo)
            spacer(document)
            renderTraite(document, trade, case.clientName, case.currency, issueDate, logo)
            if (index < active.size - 1) pageBreak(document)
        }

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return TradeExport("traites-${case.caseNumber}-v$version.docx", bytes)
    }

    private fun renderTraite(
        document: XWPFDocument,
        trade: Trade,
        lessee: String,
        currency: String,
        issueDate: LocalDate,
        logo: OrganizationLogo?,
    ) {
        logo?.let { renderLogo(document, it) }

        labelLine(document, "Tireur", traite.tireur, valueBold = true)
        labelLine(document, "Genre d'activité", traite.genreActivite, valueBold = true)
        blank(document)

        val order = document.createParagraph().also { it.alignment = ParagraphAlignment.BOTH }
        run(order, "Veuillez payer contre cette ")
        run(order, "Lettre de Change", bold = true)
        run(order, " au ")
        run(order, trade.dueDate.format(dueDateFormat), bold = true)
        run(order, " à l'ordre d'")
        run(order, traite.orderBeneficiary, bold = true)

        val amount = document.createParagraph().also { it.alignment = ParagraphAlignment.BOTH }
        run(amount, "La somme de ")
        run(amount, "$currency ${grouped(trade.amount)} = ${trade.amountInWords} Francs Guinéens", bold = true)

        text(document, "équivalant à la valeur représentative du crédit leasing à vous octroyer par la banque.")
        blank(document)

        labelLine(document, "Tiré", lessee, valueBold = false)
        labelLine(document, "Domiciliation", traite.domiciliation, valueBold = true)
        labelLine(document, "N° de compte", traite.accountNumber, valueBold = false, valueFont = ACCOUNT_FONT)
        labelLine(document, "Devise", currency, valueBold = true)
        blank(document)

        val acceptance = document.createParagraph().also { it.alignment = ParagraphAlignment.BOTH }
        run(acceptance, "POUR ACCEPTATION :\t\t\t${traite.place}, le ${longDate(issueDate)}", bold = true)
    }

    /** Places the organisation logo at the head of a traité, preserving its aspect ratio. */
    private fun renderLogo(
        document: XWPFDocument,
        logo: OrganizationLogo,
    ) {
        try {
            val image = ImageIO.read(ByteArrayInputStream(logo.bytes)) ?: return
            val width = LOGO_WIDTH_PX
            val height = (LOGO_WIDTH_PX.toDouble() * image.height / image.width).toInt().coerceAtLeast(1)
            val paragraph = document.createParagraph()
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

    private fun labelLine(
        document: XWPFDocument,
        label: String,
        value: String,
        valueBold: Boolean,
        valueFont: String = DEFAULT_FONT,
    ) {
        val paragraph = document.createParagraph()
        run(paragraph, "$label: ")
        run(paragraph, value, bold = valueBold, font = valueFont)
    }

    private fun text(
        document: XWPFDocument,
        value: String,
        bold: Boolean = false,
    ) {
        run(document.createParagraph(), value, bold = bold)
    }

    /** Adds a styled run in the traité's typography (Candara 14pt by default). */
    private fun run(
        paragraph: XWPFParagraph,
        value: String,
        bold: Boolean = false,
        font: String = DEFAULT_FONT,
    ): XWPFRun =
        paragraph.createRun().apply {
            fontFamily = font
            fontSize = FONT_SIZE_PT
            isBold = bold
            setText(value)
        }

    private fun blank(document: XWPFDocument) {
        document.createParagraph()
    }

    private fun spacer(document: XWPFDocument): XWPFParagraph {
        val paragraph = document.createParagraph()
        paragraph.createRun().addBreak()
        return paragraph
    }

    private fun pageBreak(document: XWPFDocument) {
        document.createParagraph().createRun().addBreak(BreakType.PAGE)
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
        const val FONT_SIZE_PT = 14
        const val LOGO_WIDTH_PX = 200
    }
}
