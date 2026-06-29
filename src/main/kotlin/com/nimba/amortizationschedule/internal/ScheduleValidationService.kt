package com.nimba.amortizationschedule.internal

import org.springframework.stereotype.Service
import java.io.InputStream
import java.math.BigDecimal

/**
 * Combined parsing + consistency validation of an uploaded schedule. This is the
 * single source of truth shared by the preview (NIMBA-19) and the definitive
 * upload (NIMBA-20) endpoints, so the two can never diverge. Parse errors and
 * consistency errors are merged into one list, ordered by line for presentation.
 */
@Service
class ScheduleValidationService(
    private val parser: AmortizationScheduleCsvParser,
    private val consistencyChecker: AmortizationScheduleConsistencyChecker,
) {
    fun validate(input: InputStream): ScheduleValidation {
        val parsed = parser.parse(input)
        val consistency =
            if (parsed.lines.isNotEmpty()) {
                consistencyChecker.check(parsed.lines)
            } else {
                ConsistencyResult(emptyList(), BigDecimal.ZERO)
            }
        val errors =
            (parsed.errors + consistency.errors)
                .sortedWith(compareBy({ it.lineNumber ?: Long.MIN_VALUE }, { it.column ?: "" }))
        return ScheduleValidation(
            lines = parsed.lines,
            errors = errors,
            totalLoyerTtcExcludingVr = consistency.totalLoyerTtcExcludingVr,
            fatal = parsed.fatal,
        )
    }
}

/**
 * The result of validating an upload: the typed lines, all errors (parse +
 * consistency) ordered by line, the informative total, and whether the file was
 * fundamentally unreadable ([fatal]).
 */
data class ScheduleValidation(
    val lines: List<ParsedScheduleLine>,
    val errors: List<ScheduleError>,
    val totalLoyerTtcExcludingVr: BigDecimal,
    val fatal: Boolean,
) {
    val valid: Boolean get() = errors.isEmpty()
}
