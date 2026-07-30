package com.nimba.shared

import java.math.BigDecimal

/**
 * Spells out a whole GNF amount in French, matching the bank's own document
 * convention (each word capitalized, space-separated — "Soixante Seize", not
 * "soixante-seize"): required by both the Caution de Soumission and the
 * Attestation de Capacité Financière templates ("(Deux Cent Trente Huit
 * Millions ... Francs Guinéens)"). Verified by hand against both real
 * reference amounts (238 756 476 and 2 828 096 140) before writing the caller.
 */
fun BigDecimal.toFrenchWords(): String {
    val amount = this.stripTrailingZeros().toBigInteger().toLong()
    val raw = spellInteger(amount)
    return raw.split(Regex("[- ]+")).joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}

/** The currency's name in French, spelled the same Pascal-cased way as the amount itself. */
private val CURRENCY_NAMES =
    mapOf(
        "GNF" to "Francs Guinéens",
        "USD" to "Dollars Américains",
        "EUR" to "Euros",
    )

/** The amount's name, or the code itself if a currency isn't in the known list (never blocks generation on an unexpected code). */
fun currencyNameInWords(currencyCode: String): String = CURRENCY_NAMES[currencyCode.uppercase()] ?: currencyCode

/** "(Deux Cent ... Seize Francs Guinéens)" — the amount and its currency, both spelled out, exactly as printed on the reference templates. */
fun BigDecimal.amountInWords(currencyCode: String): String = "${toFrenchWords()} ${currencyNameInWords(currencyCode)}"

private val ONES =
    arrayOf(
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
        "dix-sept",
        "dix-huit",
        "dix-neuf",
    )

private val TENS_WORD = arrayOf("", "", "vingt", "trente", "quarante", "cinquante", "soixante", "", "quatre-vingt", "")

private fun spellTens(n: Int): String {
    if (n < 20) return ONES[n]
    val tensDigit = n / 10
    val unit = n % 10
    return when (tensDigit) {
        7 -> if (unit == 1) "soixante-et-${ONES[10 + unit]}" else "soixante-${ONES[10 + unit]}"
        9 -> "quatre-vingt-${ONES[10 + unit]}"
        8 -> if (unit == 0) "quatre-vingts" else "quatre-vingt-${ONES[unit]}"
        else -> {
            val base = TENS_WORD[tensDigit]
            when (unit) {
                0 -> base
                1 -> "$base-et-un"
                else -> "$base-${ONES[unit]}"
            }
        }
    }
}

private fun spellHundreds(n: Int): String {
    if (n < 100) return spellTens(n)
    val hundredsDigit = n / 100
    val remainder = n % 100
    if (remainder == 0) return if (hundredsDigit == 1) "cent" else "${ONES[hundredsDigit]} cents"
    val hundredWord = if (hundredsDigit == 1) "cent" else "${ONES[hundredsDigit]} cent"
    return "$hundredWord ${spellTens(remainder)}"
}

private fun spellInteger(n: Long): String {
    if (n == 0L) return "zéro"
    val milliards = n / 1_000_000_000
    val millions = (n / 1_000_000) % 1000
    val milliers = (n / 1000) % 1000
    val units = (n % 1000).toInt()

    val parts = mutableListOf<String>()
    if (milliards > 0) parts += if (milliards == 1L) "un milliard" else "${spellHundreds(milliards.toInt())} milliards"
    if (millions > 0) parts += if (millions == 1L) "un million" else "${spellHundreds(millions.toInt())} millions"
    if (milliers > 0) parts += if (milliers == 1L) "mille" else "${spellHundreds(milliers.toInt())} mille"
    if (units > 0) parts += spellHundreds(units)
    return parts.joinToString(" ")
}
