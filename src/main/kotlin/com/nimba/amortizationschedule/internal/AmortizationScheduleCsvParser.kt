package com.nimba.amortizationschedule.internal

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/**
 * Reads a raw amortization-schedule CSV (format fixed in NIMBA-14) and produces a
 * [ParseResult]: the typed, structurally valid lines plus a list of errors, each
 * tied to its file line and described for a non-technical analyst. This component
 * has no Spring Web dependency and is fully testable in isolation. It only checks
 * structure and types; cross-field arithmetic consistency is a separate concern
 * (NIMBA-16).
 */
@Component
class AmortizationScheduleCsvParser {
    companion object {
        val REQUIRED_HEADERS =
            listOf(
                "numero_echeance",
                "date_echeance",
                "interet",
                "equipement",
                "assurance",
                "tracking",
                "immatriculation",
                "capital",
                "loyer_ht",
                "taxes",
                "loyer_ttc",
                "capital_restant_du",
            )
        private val REQUIRED_AMOUNT_COLUMNS =
            listOf("interet", "equipement", "assurance", "tracking", "immatriculation", "capital", "loyer_ht", "taxes", "loyer_ttc")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
        private val POSITIVE_INTEGER = Regex("""\d+""")
    }

    fun parse(input: InputStream): ParseResult {
        val decoder =
            StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
        val reader = BufferedReader(InputStreamReader(input, decoder))
        val lines = mutableListOf<ParsedScheduleLine>()
        val errors = mutableListOf<ScheduleError>()
        try {
            val format =
                CSVFormat.DEFAULT
                    .builder()
                    .setDelimiter(';')
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreSurroundingSpaces(true)
                    .setTrim(true)
                    .get()
            format.parse(reader).use { csv ->
                val missing = REQUIRED_HEADERS - csv.headerNames.toSet()
                if (missing.isNotEmpty()) {
                    return ParseResult(
                        emptyList(),
                        listOf(
                            ScheduleError(
                                1,
                                null,
                                "En-tête invalide : colonnes manquantes ou mal orthographiées : ${missing.joinToString(", ")}",
                            ),
                        ),
                        fatal = true,
                    )
                }
                var dataRows = 0
                for (record in csv) {
                    dataRows++
                    parseRecord(record, errors)?.let { lines.add(it) }
                }
                if (dataRows == 0) {
                    errors.add(ScheduleError(null, null, "Le fichier ne contient aucune ligne de données."))
                    return ParseResult(emptyList(), errors, fatal = true)
                }
            }
        } catch (ex: Exception) {
            return if (isEncodingProblem(ex)) {
                ParseResult(
                    lines,
                    listOf(
                        ScheduleError(null, null, "Le fichier n'est pas encodé en UTF-8 valide. Réenregistrez-le en UTF-8 puis réessayez."),
                    ),
                    fatal = true,
                )
            } else {
                ParseResult(
                    lines,
                    listOf(ScheduleError(null, null, "Le fichier n'a pas pu être lu comme un CSV valide.")),
                    fatal = true,
                )
            }
        }
        return ParseResult(lines, errors)
    }

    private fun parseRecord(
        record: CSVRecord,
        errors: MutableList<ScheduleError>,
    ): ParsedScheduleLine? {
        val lineNumber = record.recordNumber
        val errorsBefore = errors.size

        val numero = if (record.isSet("numero_echeance")) record.get("numero_echeance").trim() else ""
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

        val dateEcheance =
            if (isVr) {
                null
            } else {
                parseDate(record, lineNumber, errors)
            }

        val amounts = REQUIRED_AMOUNT_COLUMNS.associateWith { parseAmount(record, it, lineNumber, errors, required = true) }
        val capitalRestantDu = parseAmount(record, "capital_restant_du", lineNumber, errors, required = false)

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
        lineNumber: Long,
        errors: MutableList<ScheduleError>,
    ): LocalDate? {
        val raw = if (record.isSet("date_echeance")) record.get("date_echeance").trim() else ""
        if (raw.isEmpty()) {
            errors.add(ScheduleError(lineNumber, "date_echeance", "La date d'échéance est obligatoire (format JJ/MM/AAAA)."))
            return null
        }
        return try {
            LocalDate.parse(raw, DATE_FORMAT)
        } catch (_: Exception) {
            errors.add(ScheduleError(lineNumber, "date_echeance", "Date invalide « $raw » (format attendu : JJ/MM/AAAA)."))
            null
        }
    }

    private fun parseAmount(
        record: CSVRecord,
        column: String,
        lineNumber: Long,
        errors: MutableList<ScheduleError>,
        required: Boolean,
    ): BigDecimal? {
        val raw = if (record.isSet(column)) record.get(column).trim() else ""
        if (raw.isEmpty()) {
            if (required) errors.add(ScheduleError(lineNumber, column, "La colonne « $column » est obligatoire et ne peut pas être vide."))
            return null
        }
        val normalized = raw.replace(Regex("""[\s\u00A0]"""), "").replace(",", ".")
        return try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            errors.add(ScheduleError(lineNumber, column, "Valeur non numérique « $raw » dans la colonne « $column »."))
            null
        }
    }

    private fun isEncodingProblem(throwable: Throwable): Boolean {
        var cause: Throwable? = throwable
        while (cause != null) {
            if (cause is CharacterCodingException) return true
            cause = cause.cause
        }
        return false
    }
}
