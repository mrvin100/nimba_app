package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Builds the Word (.docx) document of a case's active trades — the lettres de change
 * (traités), one traité per échéance in two copies (bank + client), matching the
 * bank's traité layout (NIMBA-27). Each traité is rendered strictly from the
 * generated [Trade] (due date, amount in figures and in words, currency) and the
 * case (the tiré / lessee), plus the bank-side constants ([TraiteProperties]), so the
 * document is coherent with the imported schedule end to end.
 */
@Service
class TradeDocxExportService(
    private val trades: TradeRepository,
    private val schedules: AmortizationScheduleRepository,
    private val creditCases: CreditCaseModuleApi,
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

        val document = XWPFDocument()
        active.forEachIndexed { index, trade ->
            renderTraite(document, trade, case.clientName, case.currency, issueDate)
            spacer(document)
            renderTraite(document, trade, case.clientName, case.currency, issueDate)
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
    ) {
        labelLine(document, "Tireur", traite.tireur)
        labelLine(document, "Genre d'activité", traite.genreActivite)
        blank(document)
        text(
            document,
            "Veuillez payer contre cette Lettre de Change au ${trade.dueDate.format(dueDateFormat)} " +
                "à l'ordre de ${traite.orderBeneficiary}",
        )
        text(document, "La somme de")
        text(document, currency, bold = true)
        text(document, grouped(trade.amount), bold = true)
        text(document, " = ${trade.amountInWords} Francs Guinéens")
        blank(document)
        text(document, "équivalant à la valeur représentative du crédit leasing à vous octroyer par la banque.")
        labelLine(document, "Tiré", lessee)
        labelLine(document, "Domiciliation", traite.domiciliation)
        labelLine(document, "N° de compte", traite.accountNumber)
        labelLine(document, "Devise", currency)
        val acceptance = document.createParagraph().also { it.alignment = ParagraphAlignment.BOTH }
        acceptance.createRun().setText("POUR ACCEPTATION :\t\t\t${traite.place}, le ${longDate(issueDate)}")
    }

    private fun labelLine(
        document: XWPFDocument,
        label: String,
        value: String,
    ) {
        val paragraph = document.createParagraph()
        paragraph.createRun().apply {
            isBold = true
            setText("$label\t: ")
        }
        paragraph.createRun().setText(value)
    }

    private fun text(
        document: XWPFDocument,
        value: String,
        bold: Boolean = false,
    ) {
        val paragraph = document.createParagraph()
        paragraph.createRun().apply {
            isBold = bold
            setText(value)
        }
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

    private fun grouped(amount: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).format(amount.toBigInteger())
    }

    private fun longDate(date: LocalDate): String = "%02d %s %d".format(date.dayOfMonth, monthNames[date.monthValue - 1], date.year)
}
