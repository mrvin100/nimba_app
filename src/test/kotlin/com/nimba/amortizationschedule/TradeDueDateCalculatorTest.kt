package com.nimba.amortizationschedule

import com.nimba.amortizationschedule.internal.TradeDueDateCalculator
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TradeDueDateCalculatorTest {
    private val calculator = TradeDueDateCalculator()

    @Test
    fun `ordinary line - plus one month, snapped to day five`() {
        assertEquals(
            LocalDate.of(2026, 6, 5),
            calculator.dueDate(LocalDate.of(2026, 5, 1), offsetMonths = 1, fixedDayOfMonth = 5),
        )
    }

    @Test
    fun `VR line - plus two months from the last echeance, snapped to day five`() {
        assertEquals(
            LocalDate.of(2028, 6, 5),
            calculator.dueDate(LocalDate.of(2028, 4, 1), offsetMonths = 2, fixedDayOfMonth = 5),
        )
    }

    @Test
    fun `crosses the new year`() {
        assertEquals(
            LocalDate.of(2027, 1, 5),
            calculator.dueDate(LocalDate.of(2026, 11, 1), offsetMonths = 2, fixedDayOfMonth = 5),
        )
    }

    @Test
    fun `fixed day beyond the target month is clamped to the last valid day`() {
        // January 2026 + 1 month = February 2026 (28 days); day 31 -> 28.
        assertEquals(
            LocalDate.of(2026, 2, 28),
            calculator.dueDate(LocalDate.of(2026, 1, 15), offsetMonths = 1, fixedDayOfMonth = 31),
        )
    }

    @Test
    fun `zero month offset still snaps to the fixed day`() {
        assertEquals(
            LocalDate.of(2026, 5, 5),
            calculator.dueDate(LocalDate.of(2026, 5, 20), offsetMonths = 0, fixedDayOfMonth = 5),
        )
    }

    @Test
    fun `custom non-default offset of three months`() {
        assertEquals(
            LocalDate.of(2026, 8, 5),
            calculator.dueDate(LocalDate.of(2026, 5, 1), offsetMonths = 3, fixedDayOfMonth = 5),
        )
    }

    @Test
    fun `rejects an out-of-range fixed day`() {
        assertFailsWith<IllegalArgumentException> {
            calculator.dueDate(LocalDate.of(2026, 5, 1), offsetMonths = 1, fixedDayOfMonth = 32)
        }
    }
}
