package com.nimba.amortizationschedule.internal

import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Outcome of the consistency check: the inconsistencies found (same shape as parse
 * errors, for a unified list), and the informative total of all `loyer_ttc`
 * excluding the VR line.
 */
data class ConsistencyResult(
    val errors: List<ScheduleError>,
    val totalLoyerTtcExcludingVr: BigDecimal,
)

/**
 * Verifies the internal arithmetic of each schedule line (fiche métier §8.4):
 * `capital` = equipement + assurance + tracking + immatriculation;
 * `loyer_ht` = capital + interet; `loyer_ttc` = loyer_ht + taxes. A small rounding
 * tolerance absorbs actuarial rounding before flagging a discrepancy. The VR line
 * is excluded from these checks (its `interet` column carries the residual value,
 * so its structure differs) and from the informative total. This is deliberately
 * separate from the parser: the parser ensures values are present and well-typed,
 * this ensures they are arithmetically coherent.
 */
@Component
class AmortizationScheduleConsistencyChecker {
    companion object {
        /** Rounding tolerance: one unit of currency. */
        val TOLERANCE: BigDecimal = BigDecimal.ONE
    }

    fun check(lines: List<ParsedScheduleLine>): ConsistencyResult {
        val errors = mutableListOf<ScheduleError>()
        var total = BigDecimal.ZERO

        for (line in lines) {
            if (line.isResidualValue) continue

            total = total.add(line.loyerTtc)

            checkField(errors, line, "capital", line.capital, line.equipement + line.assurance + line.tracking + line.immatriculation)
            checkField(errors, line, "loyer_ht", line.loyerHt, line.capital + line.interet)
            checkField(errors, line, "loyer_ttc", line.loyerTtc, line.loyerHt + line.taxes)
        }

        return ConsistencyResult(errors, total)
    }

    private fun checkField(
        errors: MutableList<ScheduleError>,
        line: ParsedScheduleLine,
        column: String,
        found: BigDecimal,
        expected: BigDecimal,
    ) {
        if (found.subtract(expected).abs().compareTo(TOLERANCE) > 0) {
            errors.add(
                ScheduleError(
                    line.lineNumber,
                    column,
                    "Incohérence sur « $column » : valeur $found, attendu $expected.",
                ),
            )
        }
    }
}
