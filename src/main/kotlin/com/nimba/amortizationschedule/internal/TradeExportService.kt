package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.format.DateTimeFormatter
import java.util.UUID

/** A ready-to-download export: the suggested filename and the file bytes. */
data class TradeExport(
    val filename: String,
    val content: ByteArray,
)

/**
 * Builds a CSV export of a case's active trades (NIMBA-27): one row per trade with
 * the échéance number, the trade due date, the amount in figures and in words, and
 * the currency. The file is UTF-8 with a BOM so a spreadsheet opens the accented
 * words without corruption, and uses the same semicolon separator as the rest of
 * the pipeline. A 404 is raised when the case has no active trades.
 */
@Service
class TradeExportService(
    private val trades: TradeRepository,
    private val schedules: AmortizationScheduleRepository,
    private val creditCases: CreditCaseModuleApi,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    @Transactional(readOnly = true)
    fun export(creditCaseId: UUID): TradeExport {
        val case =
            creditCases.findById(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")
        val active = trades.findByCreditCaseIdAndActiveIsTrueOrderByDueDateAsc(creditCaseId)
        if (active.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun trade actif à exporter pour ce dossier.")
        }
        val version =
            schedules.findById(active.first().scheduleId).orElse(null)?.versionNumber ?: 0

        val builder = StringBuilder()
        builder.append("numero_echeance;date_echeance;montant_chiffres;montant_lettres;devise\r\n")
        active.forEach { trade ->
            builder
                .append(trade.numeroEcheance)
                .append(';')
                .append(trade.dueDate.format(dateFormat))
                .append(';')
                .append(trade.amount.toBigInteger().toString())
                .append(';')
                .append(trade.amountInWords)
                .append(';')
                .append(trade.currency)
                .append("\r\n")
        }

        val filename = "trades-${case.caseNumber}-v$version.csv"
        // Prepend the UTF-8 BOM as bytes so a spreadsheet opens the accented words correctly.
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        return TradeExport(filename, bom + builder.toString().toByteArray(Charsets.UTF_8))
    }
}
