package com.nimba.caution

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Which amount a fee line is charged on. The bank does not bill every line on
 * the same figure: the engagement and caution fees follow the caution issued
 * for the lot, the attestation fee follows the attestation (ACF/AFC) issued for
 * that same lot. Deriving the base from the documents attached to the dossier is
 * what removes the per-lot manual grid the Fiche used to require.
 */
enum class CautionFeeBase {
    /** The Caution de Soumission (SMS) issued for the lot. */
    CAUTION,

    /** The attestation (ACF or AFC) issued for the lot. */
    ATTESTATION,
}

/**
 * One line of the bank's fee schedule: a rate applied to [base], floored at
 * [minimum], then taxed. Reproduces the reference `FICHE APPROBATION.docx`
 * exactly (all four lines and their total) with the defaults below.
 *
 * [taxRate] is per line on purpose: the reference document bills the engagement
 * and delivery fees at 13% and the caution and attestation fees at 18%, so a
 * single bank-wide VAT constant would not reproduce it.
 */
data class CautionFeeLine(
    val key: String,
    /** The Fiche's abbreviated grid label, e.g. "COM ENG". */
    val label: String,
    /** The same line spelled out for the Notification letter, e.g. "Com. d'engagement". */
    val letterLabel: String,
    val rulePercent: BigDecimal,
    val minimum: BigDecimal,
    val base: CautionFeeBase,
    val taxRatePercent: BigDecimal,
) {
    /**
     * The printed left-hand label of the Fiche's section 6 and of the
     * Notification's "CONDITIONS DE BANQUE" lines, e.g.
     * `COM ENG = 1% Min GNF 1 000 000`. Built from the schedule rather than
     * retyped, so the two documents can never state different conditions.
     */
    fun describe(currency: String): String = "$label = ${rule(currency)}"

    /** The same rule phrased for the notification letter, whose labels are bolded up to the colon. */
    fun describeAsCondition(currency: String): String = "$letterLabel : ${rule(currency)}"

    private fun rule(currency: String): String = "${plain(rulePercent)}% Min $currency ${grouped(minimum)}"

    /** `max(base × rate, minimum) × (1 + tax)`, rounded to the currency's unit. */
    fun amountFor(baseAmount: BigDecimal): BigDecimal {
        val raw = baseAmount.multiply(rulePercent).divide(HUNDRED, 10, RoundingMode.HALF_UP)
        val beforeTax = raw.max(minimum)
        return beforeTax
            .multiply(HUNDRED + taxRatePercent)
            .divide(HUNDRED, 0, RoundingMode.HALF_UP)
    }

    private fun plain(value: BigDecimal): String = value.stripTrailingZeros().toPlainString().replace('.', ',')

    private fun grouped(value: BigDecimal): String =
        value
            .toBigInteger()
            .toString()
            .reversed()
            .chunked(3)
            .joinToString(" ")
            .reversed()

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal(100)
    }
}

/**
 * The dossier's fee schedule. Its four lines are fixed (they are the bank's
 * own rentability grid); their rate, floor, base and tax rate are read from the
 * dossier's content under `bareme_{row}_{taux|min|base|tva}`, falling back to
 * the reference document's values so an existing dossier keeps rendering.
 */
object CautionFeeSchedule {
    private val DEFAULTS =
        listOf(
            CautionFeeLine(
                key = "bareme_0",
                label = "COM ENG",
                letterLabel = "Com. d'engagement",
                rulePercent = BigDecimal("1"),
                minimum = BigDecimal("1000000"),
                base = CautionFeeBase.CAUTION,
                taxRatePercent = BigDecimal("13"),
            ),
            CautionFeeLine(
                key = "bareme_1",
                label = "F. CAUTION",
                letterLabel = "Frais de caution",
                rulePercent = BigDecimal("1"),
                minimum = BigDecimal("1000000"),
                base = CautionFeeBase.CAUTION,
                taxRatePercent = BigDecimal("18"),
            ),
            CautionFeeLine(
                key = "bareme_2",
                label = "F. DELIVRANCE",
                letterLabel = "Frais de délivrance",
                rulePercent = BigDecimal("0.1"),
                minimum = BigDecimal("500000"),
                base = CautionFeeBase.CAUTION,
                taxRatePercent = BigDecimal("13"),
            ),
            CautionFeeLine(
                key = "bareme_3",
                label = "F. ATTESTATION",
                letterLabel = "Frais d'attestation",
                rulePercent = BigDecimal("0.1"),
                minimum = BigDecimal("1000000"),
                base = CautionFeeBase.ATTESTATION,
                taxRatePercent = BigDecimal("18"),
            ),
        )

    /** The four lines, each overridden by whatever the dossier captured. */
    fun linesOf(content: Map<String, String>): List<CautionFeeLine> =
        DEFAULTS.map { line ->
            line.copy(
                rulePercent = content.decimal("${line.key}_taux") ?: line.rulePercent,
                minimum = content.decimal("${line.key}_min") ?: line.minimum,
                base = content["${line.key}_base"]?.let { runCatching { CautionFeeBase.valueOf(it) }.getOrNull() } ?: line.base,
                taxRatePercent = content.decimal("${line.key}_tva") ?: line.taxRatePercent,
            )
        }

    /** Reads a decimal typed with spaces or a French comma; a blank or unparseable value falls back to the default. */
    private fun Map<String, String>.decimal(key: String): BigDecimal? =
        this[key]
            ?.replace(" ", "")
            ?.replace(',', '.')
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
}
