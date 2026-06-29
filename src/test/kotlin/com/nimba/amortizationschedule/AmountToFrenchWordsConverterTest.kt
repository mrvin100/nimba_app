package com.nimba.amortizationschedule

import com.nimba.amortizationschedule.internal.AmountToFrenchWordsConverter
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmountToFrenchWordsConverterTest {
    private val converter = AmountToFrenchWordsConverter()

    private fun words(amount: Long) = converter.convert(amount, "GNF")

    @Test fun zero() = assertEquals("Zéro Francs Guinéens", words(0))

    @Test fun singleDigit() = assertEquals("Cinq Francs Guinéens", words(5))

    @Test fun sixteen() = assertEquals("Seize Francs Guinéens", words(16))

    @Test fun seventeen() = assertEquals("Dix-Sept Francs Guinéens", words(17))

    @Test fun twenty() = assertEquals("Vingt Francs Guinéens", words(20))

    @Test fun twentyOne() = assertEquals("Vingt-Un Francs Guinéens", words(21))

    @Test fun seventyOne() = assertEquals("Soixante-Onze Francs Guinéens", words(71))

    @Test fun eighty() = assertEquals("Quatre-Vingts Francs Guinéens", words(80))

    @Test fun eightyOne() = assertEquals("Quatre-Vingt-Un Francs Guinéens", words(81))

    @Test fun ninety() = assertEquals("Quatre-Vingt-Dix Francs Guinéens", words(90))

    @Test fun ninetyOne() = assertEquals("Quatre-Vingt-Onze Francs Guinéens", words(91))

    @Test fun ninetyNine() = assertEquals("Quatre-Vingt-Dix-Neuf Francs Guinéens", words(99))

    @Test fun hundred() = assertEquals("Cent Francs Guinéens", words(100))

    @Test fun twoHundred() = assertEquals("Deux Cents Francs Guinéens", words(200))

    @Test fun twoHundredOne() = assertEquals("Deux Cent Un Francs Guinéens", words(201))

    @Test fun thousand() = assertEquals("Mille Francs Guinéens", words(1_000))

    @Test fun twoThousand() = assertEquals("Deux Mille Francs Guinéens", words(2_000))

    @Test fun eightyThousand_noPluralBeforeMille() = assertEquals("Quatre-Vingt Mille Francs Guinéens", words(80_000))

    @Test fun twoHundredThousand_noPluralCentBeforeMille() = assertEquals("Deux Cent Mille Francs Guinéens", words(200_000))

    @Test fun oneMillion() = assertEquals("Un Million Francs Guinéens", words(1_000_000))

    @Test fun twoMillions() = assertEquals("Deux Millions Francs Guinéens", words(2_000_000))

    @Test fun oneBillion() = assertEquals("Un Milliard Francs Guinéens", words(1_000_000_000))

    @Test
    fun realCaseFirstLoyer() =
        assertEquals(
            "Cinq Cent Trente-Neuf Millions Cinq Cent Soixante-Onze Mille Cent Vingt-Trois Francs Guinéens",
            words(539_571_123),
        )

    @Test
    fun realCaseResidualValue() =
        assertEquals(
            "Cinquante-Quatre Millions Deux Cent Quatre-Vingt Mille Francs Guinéens",
            words(54_280_000),
        )

    @Test
    fun roundsBigDecimalHalfUp() =
        assertEquals(
            "Cinq Cent Trente-Neuf Millions Cinq Cent Soixante-Onze Mille Cent Vingt-Trois Francs Guinéens",
            converter.convert(BigDecimal("539571122.79"), "GNF"),
        )

    @Test
    fun rejectsNegative() {
        assertFailsWith<IllegalArgumentException> { words(-1) }
    }

    @Test
    fun rejectsUnknownCurrency() {
        assertFailsWith<IllegalArgumentException> { converter.convert(100, "XOF") }
    }
}
