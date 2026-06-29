package com.nimba.amortizationschedule.internal

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A structurally valid, typed schedule line produced by the parser. Values are
 * faithfully transcribed from the file with no business transformation — the
 * cross-field arithmetic consistency check is a separate concern (NIMBA-16).
 * [lineNumber] is the line in the source file, for tying preview rows and errors
 * together. [dateEcheance] is null for the VR line.
 */
data class ParsedScheduleLine(
    val lineNumber: Long,
    val numeroEcheance: String,
    val dateEcheance: LocalDate?,
    val interet: BigDecimal,
    val equipement: BigDecimal,
    val assurance: BigDecimal,
    val tracking: BigDecimal,
    val immatriculation: BigDecimal,
    val capital: BigDecimal,
    val loyerHt: BigDecimal,
    val taxes: BigDecimal,
    val loyerTtc: BigDecimal,
    val capitalRestantDu: BigDecimal?,
) {
    val isResidualValue: Boolean
        get() = numeroEcheance.equals("VR", ignoreCase = true)
}

/**
 * Outcome of parsing a CSV: the typed lines that parsed cleanly and the errors
 * found (each tied to its line). A file with errors still returns the lines that
 * did parse, so the preview can show both.
 */
data class ParseResult(
    val lines: List<ParsedScheduleLine>,
    val errors: List<ScheduleError>,
) {
    val hasErrors: Boolean get() = errors.isNotEmpty()
}
