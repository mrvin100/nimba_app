package com.nimba.shared

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class FrenchNumberWordsTest {
    @Test
    fun `spells the real SMS amount exactly as the reference document`() {
        assertEquals(
            "Deux Cent Trente Huit Millions Sept Cent Cinquante Six Mille Quatre Cent Soixante Seize",
            BigDecimal("238756476").toFrenchWords(),
        )
    }

    @Test
    fun `spells the real ACF amount exactly as the reference document`() {
        assertEquals(
            "Deux Milliards Huit Cent Vingt Huit Millions Quatre Vingt Seize Mille Cent Quarante",
            BigDecimal("2828096140").toFrenchWords(),
        )
    }

    @Test
    fun `handles the tricky French tens (soixante-dix, quatre-vingts, quatre-vingt-dix)`() {
        assertEquals("Soixante Dix", BigDecimal("70").toFrenchWords())
        assertEquals("Soixante Et Onze", BigDecimal("71").toFrenchWords())
        assertEquals("Quatre Vingts", BigDecimal("80").toFrenchWords())
        assertEquals("Quatre Vingt Un", BigDecimal("81").toFrenchWords())
        assertEquals("Quatre Vingt Dix", BigDecimal("90").toFrenchWords())
        assertEquals("Quatre Vingt Onze", BigDecimal("91").toFrenchWords())
        assertEquals("Vingt Et Un", BigDecimal("21").toFrenchWords())
    }

    @Test
    fun `does not pluralize cent or vingt when followed by another number`() {
        assertEquals("Deux Cents", BigDecimal("200").toFrenchWords())
        assertEquals("Deux Cent Un", BigDecimal("201").toFrenchWords())
    }

    @Test
    fun `zero and small numbers`() {
        assertEquals("Zéro", BigDecimal("0").toFrenchWords())
        assertEquals("Un", BigDecimal("1").toFrenchWords())
        assertEquals("Mille", BigDecimal("1000").toFrenchWords())
        assertEquals("Un Million", BigDecimal("1000000").toFrenchWords())
    }
}
