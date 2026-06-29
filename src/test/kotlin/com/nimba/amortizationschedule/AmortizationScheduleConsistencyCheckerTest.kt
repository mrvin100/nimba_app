package com.nimba.amortizationschedule

import com.nimba.amortizationschedule.internal.AmortizationScheduleConsistencyChecker
import com.nimba.amortizationschedule.internal.AmortizationScheduleCsvParser
import com.nimba.amortizationschedule.internal.ParsedScheduleLine
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmortizationScheduleConsistencyCheckerTest {
    private val parser = AmortizationScheduleCsvParser()
    private val checker = AmortizationScheduleConsistencyChecker()

    private fun line(
        numero: String,
        interet: String,
        equipement: String,
        assurance: String,
        tracking: String,
        immatriculation: String,
        capital: String,
        loyerHt: String,
        taxes: String,
        loyerTtc: String,
    ) = ParsedScheduleLine(
        lineNumber = 2,
        numeroEcheance = numero,
        dateEcheance = if (numero == "VR") null else LocalDate.of(2026, 5, 1),
        interet = BigDecimal(interet),
        equipement = BigDecimal(equipement),
        assurance = BigDecimal(assurance),
        tracking = BigDecimal(tracking),
        immatriculation = BigDecimal(immatriculation),
        capital = BigDecimal(capital),
        loyerHt = BigDecimal(loyerHt),
        taxes = BigDecimal(taxes),
        loyerTtc = BigDecimal(loyerTtc),
        capitalRestantDu = null,
    )

    @Test
    fun `the valid example file passes the consistency checks`() {
        val parsed = parser.parse(File("docs/examples/exemple-echeancier-valide.csv").inputStream())
        assertTrue(parsed.lines.isNotEmpty())

        val result = checker.check(parsed.lines)

        assertTrue(result.errors.isEmpty(), "unexpected consistency errors: ${result.errors}")
        // Informative total of loyer_ttc must exclude the VR line.
        val expected = parsed.lines.filterNot { it.isResidualValue }.fold(BigDecimal.ZERO) { acc, l -> acc + l.loyerTtc }
        assertEquals(0, result.totalLoyerTtcExcludingVr.compareTo(expected))
        assertEquals(24, parsed.lines.count { !it.isResidualValue })
    }

    @Test
    fun `flags a line whose loyer_ttc does not equal loyer_ht plus taxes`() {
        val bad = line("3", "100", "200", "300", "400", "500", "1400", "1500", "100", "999999")

        val result = checker.check(listOf(bad))

        val error = result.errors.single()
        assertEquals("loyer_ttc", error.column)
        assertEquals(2L, error.lineNumber)
    }

    @Test
    fun `flags a line whose capital does not equal the sum of its components`() {
        val bad = line("1", "100", "200", "300", "400", "500", "9999", "10099", "100", "10199")

        val result = checker.check(listOf(bad))

        assertTrue(result.errors.any { it.column == "capital" })
    }

    @Test
    fun `tolerates a sub-unit rounding difference`() {
        // capital off by 0.40 from the component sum (1400.00 vs 1400.40): within tolerance.
        val line = line("1", "100.00", "200.10", "300.10", "400.10", "500.10", "1400.00", "1500.00", "100.00", "1600.00")

        val result = checker.check(listOf(line))

        assertTrue(result.errors.isEmpty(), "${result.errors}")
    }

    @Test
    fun `excludes the VR line from checks and from the total`() {
        // A VR line whose structure would fail the ordinary checks must not be flagged.
        val vr = line("VR", "46000000", "0", "0", "0", "0", "0", "0", "8280000", "54280000")

        val result = checker.check(listOf(vr))

        assertTrue(result.errors.isEmpty(), "${result.errors}")
        assertEquals(0, result.totalLoyerTtcExcludingVr.compareTo(BigDecimal.ZERO))
    }

    @Test
    fun `detects the inconsistency in the invalid example file`() {
        val parsed = parser.parse(File("docs/examples/exemple-echeancier-invalide.csv").inputStream())

        val result = checker.check(parsed.lines)

        assertTrue(result.errors.any { it.column == "loyer_ttc" }, "expected a loyer_ttc inconsistency: ${result.errors}")
    }
}
