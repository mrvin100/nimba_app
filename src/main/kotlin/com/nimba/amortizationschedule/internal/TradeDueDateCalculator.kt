package com.nimba.amortizationschedule.internal

import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Computes a trade's due date from a source line's date and the schedule's offset
 * parameters (fiche métier §4): shift the base date by the given number of months,
 * then snap to the fixed day of the resulting month. If that day exceeds the
 * month's length, it is clamped to the last valid day rather than overflowing into
 * the next month. Isolated from the rest of generation so the date rule can be
 * tested exhaustively on its own. The caller supplies the ordinary or VR offset.
 */
@Component
class TradeDueDateCalculator {
    fun dueDate(
        baseDate: LocalDate,
        offsetMonths: Int,
        fixedDayOfMonth: Int,
    ): LocalDate {
        require(offsetMonths >= 0) { "Le décalage en mois ne peut pas être négatif : $offsetMonths" }
        require(fixedDayOfMonth in 1..31) { "Le jour fixe doit être compris entre 1 et 31 : $fixedDayOfMonth" }
        val shifted = baseDate.plusMonths(offsetMonths.toLong())
        return shifted.withDayOfMonth(minOf(fixedDayOfMonth, shifted.lengthOfMonth()))
    }
}
