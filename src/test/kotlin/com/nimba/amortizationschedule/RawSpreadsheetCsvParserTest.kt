package com.nimba.amortizationschedule

import com.nimba.amortizationschedule.internal.AmortizationScheduleCsvParser
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The parser must ingest the real working-file export (ETS OC ET FRERES): a comma
 * CSV with a leading parameter block, French headers, a trailing TOTAL row, and US
 * (M/d) dates — while preserving integrity. The month/day order is resolved by the
 * coherent monthly progression, so the dates are read correctly (May, June, …), not
 * mis-parsed as day/month.
 */
class RawSpreadsheetCsvParserTest {
    private val parser = AmortizationScheduleCsvParser()

    @Test
    fun `parses the raw ETS OC ET FRERES spreadsheet export end to end`() {
        val result = parser.parse(File("docs/examples/ta-oc-et-freres-brut.csv").inputStream())

        assertFalse(result.hasErrors, "raw export should parse cleanly: ${result.errors}")
        assertEquals(25, result.lines.size, "24 échéances + VR")
        assertEquals(1, result.lines.count { it.isResidualValue })

        val first = result.lines.first()
        assertEquals("1", first.numeroEcheance)
        assertEquals(LocalDate.of(2026, 5, 1), first.dateEcheance, "5/1/2026 is May 1st, not Jan 5th")
        assertEquals(BigDecimal("539571123"), first.loyerTtc)

        val lastOrdinary = result.lines.first { it.numeroEcheance == "24" }
        assertEquals(LocalDate.of(2028, 4, 1), lastOrdinary.dateEcheance)

        val vr = result.lines.single { it.isResidualValue }
        assertEquals(BigDecimal("54280000"), vr.loyerTtc)
    }
}
