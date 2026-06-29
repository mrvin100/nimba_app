package com.nimba.amortizationschedule

import com.nimba.amortizationschedule.internal.AmortizationScheduleCsvParser
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the CSV parser, run in isolation (no Spring, no database). The
 * valid/invalid example files (NIMBA-14) are used as fixtures alongside inline
 * cases for the structural edge cases.
 */
class AmortizationScheduleCsvParserTest {
    private val parser = AmortizationScheduleCsvParser()
    private val header =
        "numero_echeance;date_echeance;interet;equipement;assurance;tracking;immatriculation;capital;loyer_ht;taxes;loyer_ttc;capital_restant_du"

    private fun parse(content: String) = parser.parse(content.byteInputStream(Charsets.UTF_8))

    private fun ordinaryRow(numero: String) = "$numero;01/05/2026;100;200;300;400;500;1400;1500;100;1600;5000"

    @Test
    fun `parses the valid example file - 24 echeances plus VR, no errors`() {
        val result = parser.parse(File("docs/examples/exemple-echeancier-valide.csv").inputStream())

        assertFalse(result.hasErrors, "valid file should have no parse errors: ${result.errors}")
        assertEquals(25, result.lines.size)
        assertEquals(1, result.lines.count { it.isResidualValue })
        val first = result.lines.first()
        assertEquals("1", first.numeroEcheance)
        assertEquals(BigDecimal("539571122.79"), first.loyerTtc)
    }

    @Test
    fun `the invalid example file surfaces the empty obligatory field`() {
        val result = parser.parse(File("docs/examples/exemple-echeancier-invalide.csv").inputStream())

        assertTrue(result.hasErrors)
        assertTrue(
            result.errors.any { it.column == "capital" },
            "expected an error on the empty capital column: ${result.errors}",
        )
    }

    @Test
    fun `rejects a header that does not match the 12 expected columns`() {
        val result = parse("a;b;c\n1;2;3")

        assertEquals(1, result.errors.size)
        assertTrue(result.lines.isEmpty())
        assertEquals(1L, result.errors.first().lineNumber)
    }

    @Test
    fun `reports an empty obligatory column with its line and column`() {
        val result = parse("$header\n1;01/05/2026;;200;300;400;500;1400;1500;100;1600;5000")

        val error = result.errors.single()
        assertEquals("interet", error.column)
        assertNotNull(error.lineNumber)
        assertTrue(result.lines.isEmpty())
    }

    @Test
    fun `reports a non-numeric value where a number is expected`() {
        val result = parse("$header\n1;01/05/2026;abc;200;300;400;500;1400;1500;100;1600;5000")

        assertTrue(result.errors.any { it.column == "interet" && it.message.contains("non numérique") })
    }

    @Test
    fun `rejects a file with only a header and no data rows`() {
        val result = parse(header)

        assertTrue(result.lines.isEmpty())
        assertTrue(result.errors.any { it.message.contains("aucune ligne") })
    }

    @Test
    fun `accepts the literal VR in numero_echeance with an empty date`() {
        val result = parse("$header\nVR;;46000000;0;0;0;0;0;0;8280000;54280000;")

        assertFalse(result.hasErrors, "${result.errors}")
        val vr = result.lines.single()
        assertTrue(vr.isResidualValue)
        assertNull(vr.dateEcheance)
        assertEquals(BigDecimal("54280000"), vr.loyerTtc)
    }

    @Test
    fun `parses a schedule that has no VR line`() {
        val result = parse("$header\n${ordinaryRow("1")}\n${ordinaryRow("2")}")

        assertFalse(result.hasErrors, "${result.errors}")
        assertEquals(2, result.lines.size)
        assertEquals(0, result.lines.count { it.isResidualValue })
    }

    @Test
    fun `rejects a non-integer, non-VR echeance number`() {
        val result = parse("$header\n1bis;01/05/2026;100;200;300;400;500;1400;1500;100;1600;5000")

        assertTrue(result.errors.any { it.column == "numero_echeance" })
    }

    @Test
    fun `rejects an invalid date`() {
        val result = parse("$header\n1;32/13/2026;100;200;300;400;500;1400;1500;100;1600;5000")

        assertTrue(result.errors.any { it.column == "date_echeance" })
    }

    @Test
    fun `tolerates thousands spaces and a decimal comma in amounts`() {
        val result = parse("$header\n1;01/05/2026;1 234 567,89;200;300;400;500;1400;1500;100;1600;5000")

        assertFalse(result.hasErrors, "${result.errors}")
        assertEquals(BigDecimal("1234567.89"), result.lines.single().interet)
    }

    @Test
    fun `reports a clear error for non UTF-8 content rather than crashing`() {
        val bytes = "$header\n".toByteArray(Charsets.UTF_8) + byteArrayOf(0xFF.toByte(), 0xFE.toByte())

        val result = parser.parse(bytes.inputStream())

        assertTrue(result.errors.any { it.message.contains("UTF-8") }, "${result.errors}")
    }

    @Test
    fun `optional capital_restant_du may be empty`() {
        val result = parse("$header\n1;01/05/2026;100;200;300;400;500;1400;1500;100;1600;")

        assertFalse(result.hasErrors, "${result.errors}")
        assertNull(result.lines.single().capitalRestantDu)
    }
}
