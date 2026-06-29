package com.nimba.amortizationschedule.internal

import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Converts a non-negative integer amount to its full French spelling, then applies
 * the bank's capitalisation convention (every word's first letter upper-cased,
 * including each part of a hyphenated compound) and appends the currency name.
 *
 * Accord rules implemented: «cent» and «quatre-vingt» take an «s» only when they
 * end the spoken number (multiplied, with nothing following) — suppressed before
 * «mille» but kept before the nouns «million»/«milliard»; «mille» is invariable;
 * «million»/«milliard» take an «s» in the plural. Following the bank's reference
 * document, tens-units compounds are hyphenated without «et» (71 → «Soixante-Onze»).
 *
 * Verified against the two real cases (539571123, 54280000) at the character level.
 * Written in-house because no mature JVM library covers these rules reliably, and
 * the spelled amount on a lettre de change has legal weight.
 */
@Component
class AmountToFrenchWordsConverter {
    // Single place to extend currency names; values are lower-cased and capitalised
    // uniformly with the rest of the wording.
    private val currencyNames = mapOf("GNF" to "francs guinéens")

    private val units =
        listOf(
            "zéro",
            "un",
            "deux",
            "trois",
            "quatre",
            "cinq",
            "six",
            "sept",
            "huit",
            "neuf",
            "dix",
            "onze",
            "douze",
            "treize",
            "quatorze",
            "quinze",
            "seize",
        )
    private val tens = mapOf(2 to "vingt", 3 to "trente", 4 to "quarante", 5 to "cinquante", 6 to "soixante")
    private val scales = listOf("", "mille", "million", "milliard")

    fun convert(
        amount: Long,
        currency: String,
    ): String {
        require(amount >= 0) { "Le montant ne peut pas être négatif : $amount" }
        val currencyName =
            currencyNames[currency]
                ?: throw IllegalArgumentException("Devise non supportée pour la conversion en lettres : $currency")
        return capitalizeWords("${numberToWords(amount)} $currencyName")
    }

    /** Rounds to the nearest whole unit (no significant decimals in GNF) then converts. */
    fun convert(
        amount: BigDecimal,
        currency: String,
    ): String = convert(amount.setScale(0, RoundingMode.HALF_UP).longValueExact(), currency)

    private fun numberToWords(n: Long): String {
        if (n == 0L) return "zéro"
        require(n < 1_000_000_000_000L) { "Montant hors de l'intervalle supporté : $n" }

        val groups = mutableListOf<Int>()
        var x = n
        while (x > 0) {
            groups.add((x % 1000).toInt())
            x /= 1000
        }

        val parts = mutableListOf<String>()
        for (i in groups.indices.reversed()) {
            val g = groups[i]
            if (g == 0) continue
            val scale = scales[i]
            val groupWords = spellGroup(g, scale)
            parts +=
                when (scale) {
                    "" -> groupWords
                    "mille" -> if (g == 1) "mille" else "$groupWords mille"
                    else -> "$groupWords $scale" + if (g > 1) "s" else ""
                }
        }
        return parts.joinToString(" ")
    }

    private fun spellGroup(
        g: Int,
        scaleName: String,
    ): String {
        val scaleIsMille = scaleName == "mille"
        val hundreds = g / 100
        val remainder = g % 100
        val builder = StringBuilder()

        if (hundreds > 0) {
            builder.append(if (hundreds == 1) "cent" else "${units[hundreds]} cent")
            if (hundreds > 1 && remainder == 0 && !scaleIsMille) builder.append("s")
        }
        if (remainder > 0) {
            if (hundreds > 0) builder.append(" ")
            builder.append(spellTens(remainder, allowVingtPlural = !scaleIsMille))
        }
        return builder.toString()
    }

    private fun spellTens(
        n: Int,
        allowVingtPlural: Boolean,
    ): String {
        if (n < 17) return units[n]
        if (n < 20) return "dix-${units[n - 10]}"
        val t = n / 10
        val u = n % 10
        return when (t) {
            2, 3, 4, 5, 6 -> tens.getValue(t) + if (u == 0) "" else "-${units[u]}"
            7 -> "soixante-${sub(n - 60)}"
            8 -> if (u == 0) "quatre-vingt" + (if (allowVingtPlural) "s" else "") else "quatre-vingt-${units[u]}"
            else -> "quatre-vingt-${sub(n - 80)}" // 90..99
        }
    }

    /** Spelling of 10..19 (used inside the 70s and 90s). */
    private fun sub(n: Int): String = if (n < 17) units[n] else "dix-${units[n - 10]}"

    private fun capitalizeWords(text: String): String =
        text
            .split(" ")
            .joinToString(" ") { word ->
                word.split("-").joinToString("-") { part -> part.replaceFirstChar { it.uppercaseChar() } }
            }
}
