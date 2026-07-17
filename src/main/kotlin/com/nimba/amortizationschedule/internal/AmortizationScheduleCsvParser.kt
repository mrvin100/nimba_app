package com.nimba.amortizationschedule.internal

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.StringReader
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit

/**
 * Reads a raw amortization-schedule CSV and produces a [ParseResult]: the typed,
 * structurally valid lines plus a list of errors, each tied to its file line and
 * described for a non-technical analyst.
 *
 * It is tolerant of what analysts actually export from Excel while keeping data
 * integrity: the delimiter (',' or ';'), a leading block of parameter rows above the
 * table, French or snake_case column headers, and a trailing TOTAL row are all
 * accepted. Ambiguous formats are resolved deterministically — the date format
 * (dd/MM vs MM/dd) is chosen as the one giving a coherent monthly progression, and
 * thousands separators are stripped only when unambiguous — so a value is never
 * silently mis-read. Cross-field arithmetic consistency remains a separate concern
 * (NIMBA-16).
 *
 * The file is expected to be UTF-8 (with or without a BOM), the encoding of every
 * modern "CSV UTF-8" export. Older Excel versions instead save "CSV (comma
 * delimited)" / "CSV (délimité par des virgules)" in the machine's ANSI codepage —
 * Windows-1252 for a French/Western-European install — which only differs from
 * UTF-8 once an accented character appears. When strict UTF-8 decoding fails, the
 * same bytes are retried as Windows-1252 before the file is rejected, so analysts on
 * an older Excel are not forced to re-save their export to upload it.
 */
@Component
class AmortizationScheduleCsvParser {
    companion object {
        private val LEGACY_EXCEL_CHARSET: Charset = Charset.forName("windows-1252")

        // Internal column -> accepted header spellings (compared after normalisation:
        // lower-case, accents removed, non-alphanumerics dropped).
        private val COLUMN_ALIASES: Map<String, Set<String>> =
            mapOf(
                "numero_echeance" to setOf("numeroecheance", "numero", "n", "no", "num"),
                "date_echeance" to setOf("dateecheance", "date"),
                "interet" to setOf("interet"),
                "equipement" to setOf("equipement"),
                "assurance" to setOf("assurance"),
                "tracking" to setOf("tracking"),
                "immatriculation" to setOf("immatriculation", "immat"),
                "capital" to setOf("capital"),
                "loyer_ht" to setOf("loyerht"),
                "taxes" to setOf("taxes", "taxe"),
                "loyer_ttc" to setOf("loyerttc"),
                "capital_restant_du" to setOf("capitalrestantdu", "crd"),
            )
        private val REQUIRED_COLUMNS = COLUMN_ALIASES.keys
        private val REQUIRED_AMOUNT_COLUMNS =
            listOf("interet", "equipement", "assurance", "tracking", "immatriculation", "capital", "loyer_ht", "taxes", "loyer_ttc")
        private val DATE_FORMATS =
            listOf(
                DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT),
                DateTimeFormatter.ofPattern("M/d/uuuu").withResolverStyle(ResolverStyle.STRICT),
            )
        private val CANONICAL_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        private val POSITIVE_INTEGER = Regex("""\d+""")

        private fun normalize(value: String): String =
            Normalizer
                .normalize(value.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^a-z0-9]"), "")
    }

    fun parse(input: InputStream): ParseResult {
        val lines = mutableListOf<ParsedScheduleLine>()
        val errors = mutableListOf<ScheduleError>()
        try {
            val content = decode(input.readAllBytes())
            val delimiter = if (firstLine(content).count { it == ',' } > firstLine(content).count { it == ';' }) ',' else ';'
            // When the field delimiter is a comma, any comma inside a (quoted) amount can
            // only be a thousands separator — a file that used ',' as its decimal mark
            // could not also use it to separate fields. This resolves the "305,257"
            // (= 305257, not 305.257) ambiguity for Excel's "CSV UTF-8 (comma delimited)".
            val commaIsDelimiter = delimiter == ','
            val format =
                CSVFormat.DEFAULT
                    .builder()
                    .setDelimiter(delimiter)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .get()
            val records = format.parse(StringReader(content)).use { it.records }

            val headerIndex = records.indexOfFirst { columnIndex(it) != null }
            if (headerIndex < 0) {
                return ParseResult(
                    emptyList(),
                    listOf(
                        ScheduleError(
                            1,
                            null,
                            "En-tête introuvable : le tableau doit comporter les colonnes ${REQUIRED_COLUMNS.joinToString(", ")}.",
                        ),
                    ),
                    fatal = true,
                )
            }
            val columns = columnIndex(records[headerIndex])!!

            val dataRecords = mutableListOf<CSVRecord>()
            for (record in records.drop(headerIndex + 1)) {
                val numero = cell(record, columns, "numero_echeance")
                if (normalize(numero) == "total") break // the TOTAL row marks the end of the table
                if (record.all { it.isBlank() }) continue // skip blank spacer rows
                dataRecords.add(record)
            }
            if (dataRecords.isEmpty()) {
                errors.add(ScheduleError(null, null, "Le fichier ne contient aucune ligne de données."))
                return ParseResult(emptyList(), errors, fatal = true)
            }

            val dateFormat = detectDateFormat(dataRecords, columns)
            for (record in dataRecords) {
                parseRecord(record, columns, dateFormat, commaIsDelimiter, errors)?.let { lines.add(it) }
            }
        } catch (ex: Exception) {
            return if (isEncodingProblem(ex)) {
                fatal(
                    "Le fichier n'est encodé ni en UTF-8 ni dans l'encodage Windows (ANSI) " +
                        "d'Excel. Réenregistrez-le en UTF-8 puis réessayez.",
                )
            } else {
                fatal("Le fichier n'a pas pu être lu comme un CSV valide.")
            }
        }
        return ParseResult(lines, errors)
    }

    /**
     * Decodes the raw bytes as UTF-8, falling back to Windows-1252 — the codepage an
     * older Excel writes a "CSV (comma delimited)" export in — when the bytes are not
     * valid UTF-8. Windows-1252 assigns a character to almost every byte value, so this
     * fallback essentially always succeeds; only bytes that are invalid under both
     * encodings surface as an unreadable file.
     */
    private fun decode(bytes: ByteArray): String =
        try {
            decodeStrict(bytes, StandardCharsets.UTF_8)
        } catch (utf8Error: CharacterCodingException) {
            try {
                decodeStrict(bytes, LEGACY_EXCEL_CHARSET)
            } catch (_: CharacterCodingException) {
                throw utf8Error
            }
        }

    private fun decodeStrict(
        bytes: ByteArray,
        charset: Charset,
    ): String =
        charset
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()

    /** Column-name -> index map when the record is the table header, else null. */
    private fun columnIndex(record: CSVRecord): Map<String, Int>? {
        val normalized = record.values().map { normalize(it) }
        val mapping = mutableMapOf<String, Int>()
        for ((column, aliases) in COLUMN_ALIASES) {
            val index = normalized.indexOfFirst { it.isNotEmpty() && it in aliases }
            if (index < 0) return null
            mapping[column] = index
        }
        return mapping
    }

    private fun cell(
        record: CSVRecord,
        columns: Map<String, Int>,
        column: String,
    ): String {
        val index = columns.getValue(column)
        return if (index < record.size()) record.get(index).trim() else ""
    }

    /**
     * Chooses the date pattern giving the most coherent monthly progression (≈30-day
     * steps). Falls back to the canonical dd/MM/uuuu so a legitimate ambiguous file is
     * read the documented way rather than guessed.
     */
    private fun detectDateFormat(
        dataRecords: List<CSVRecord>,
        columns: Map<String, Int>,
    ): DateTimeFormatter {
        val raw =
            dataRecords
                .map { cell(it, columns, "numero_echeance") to cell(it, columns, "date_echeance") }
                .filter { !it.first.equals("VR", ignoreCase = true) && it.second.isNotBlank() }
                .map { it.second }
        if (raw.isEmpty()) return CANONICAL_DATE

        var best: DateTimeFormatter? = null
        var bestScore = -1
        for (formatter in DATE_FORMATS + CANONICAL_DATE) {
            val dates = raw.mapNotNull { runCatching { LocalDate.parse(it, formatter) }.getOrNull() }
            if (dates.size != raw.size) continue // this pattern cannot read every date
            val monthly = dates.zipWithNext().count { (a, b) -> ChronoUnit.DAYS.between(a, b) in 26..35 }
            if (monthly > bestScore) {
                bestScore = monthly
                best = formatter
            }
        }
        return best ?: CANONICAL_DATE
    }

    private fun parseRecord(
        record: CSVRecord,
        columns: Map<String, Int>,
        dateFormat: DateTimeFormatter,
        commaIsDelimiter: Boolean,
        errors: MutableList<ScheduleError>,
    ): ParsedScheduleLine? {
        val lineNumber = record.recordNumber
        val errorsBefore = errors.size

        val numero = cell(record, columns, "numero_echeance")
        if (numero.isEmpty()) {
            errors.add(ScheduleError(lineNumber, "numero_echeance", "Le numéro d'échéance est obligatoire."))
        }
        val isVr = numero.equals("VR", ignoreCase = true)
        if (numero.isNotEmpty() && !isVr && !POSITIVE_INTEGER.matches(numero)) {
            errors.add(
                ScheduleError(
                    lineNumber,
                    "numero_echeance",
                    "Le numéro d'échéance doit être un entier positif ou « VR » (trouvé : « $numero »).",
                ),
            )
        }

        val dateEcheance = if (isVr) null else parseDate(record, columns, dateFormat, lineNumber, errors)
        val amounts =
            REQUIRED_AMOUNT_COLUMNS.associateWith {
                parseAmount(record, columns, it, commaIsDelimiter, lineNumber, errors, required = true)
            }
        val capitalRestantDu =
            parseAmount(record, columns, "capital_restant_du", commaIsDelimiter, lineNumber, errors, required = false)

        if (errors.size > errorsBefore) return null

        return ParsedScheduleLine(
            lineNumber = lineNumber,
            numeroEcheance = numero,
            dateEcheance = dateEcheance,
            interet = amounts.getValue("interet")!!,
            equipement = amounts.getValue("equipement")!!,
            assurance = amounts.getValue("assurance")!!,
            tracking = amounts.getValue("tracking")!!,
            immatriculation = amounts.getValue("immatriculation")!!,
            capital = amounts.getValue("capital")!!,
            loyerHt = amounts.getValue("loyer_ht")!!,
            taxes = amounts.getValue("taxes")!!,
            loyerTtc = amounts.getValue("loyer_ttc")!!,
            capitalRestantDu = capitalRestantDu,
        )
    }

    private fun parseDate(
        record: CSVRecord,
        columns: Map<String, Int>,
        dateFormat: DateTimeFormatter,
        lineNumber: Long,
        errors: MutableList<ScheduleError>,
    ): LocalDate? {
        val raw = cell(record, columns, "date_echeance")
        if (raw.isEmpty()) {
            errors.add(ScheduleError(lineNumber, "date_echeance", "La date d'échéance est obligatoire."))
            return null
        }
        return try {
            LocalDate.parse(raw, dateFormat)
        } catch (_: Exception) {
            errors.add(ScheduleError(lineNumber, "date_echeance", "Date invalide « $raw »."))
            null
        }
    }

    private fun parseAmount(
        record: CSVRecord,
        columns: Map<String, Int>,
        column: String,
        commaIsDelimiter: Boolean,
        lineNumber: Long,
        errors: MutableList<ScheduleError>,
        required: Boolean,
    ): BigDecimal? {
        val raw = cell(record, columns, column)
        if (raw.isEmpty()) {
            if (required) errors.add(ScheduleError(lineNumber, column, "La colonne « $column » est obligatoire et ne peut pas être vide."))
            return null
        }
        return try {
            BigDecimal(normalizeAmount(raw, commaIsDelimiter))
        } catch (_: NumberFormatException) {
            errors.add(ScheduleError(lineNumber, column, "Valeur non numérique « $raw » dans la colonne « $column »."))
            null
        }
    }

    /**
     * Normalises a money string to a plain decimal. Whitespace is dropped. When the file
     * is comma-delimited ([commaIsDelimiter]), every comma is unambiguously a thousands
     * separator (so "305,257" is the integer 305257, not the decimal 305.257) and '.' is
     * the decimal mark. Otherwise (semicolon-delimited) the decimal mark may be ',' or
     * '.', so it is inferred per value: grouped integers (several ',' or several '.')
     * have their separators removed, and a lone ',' is a European decimal comma — a lone
     * '.' is kept as the decimal point.
     */
    private fun normalizeAmount(
        raw: String,
        commaIsDelimiter: Boolean,
    ): String {
        val cleaned = raw.replace(Regex("""[\s ]"""), "")
        if (commaIsDelimiter) {
            return cleaned.replace(",", "")
        }
        val commas = cleaned.count { it == ',' }
        val dots = cleaned.count { it == '.' }
        return when {
            commas > 1 && dots == 0 -> cleaned.replace(",", "")
            dots > 1 && commas == 0 -> cleaned.replace(".", "")
            commas == 1 && dots == 0 -> cleaned.replace(",", ".")
            else -> cleaned
        }
    }

    private fun firstLine(content: String): String = content.lineSequence().firstOrNull().orEmpty()

    private fun fatal(message: String) = ParseResult(emptyList(), listOf(ScheduleError(null, null, message)), fatal = true)

    private fun isEncodingProblem(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is CharacterCodingException) return true
            cause = cause.cause
        }
        return false
    }
}
