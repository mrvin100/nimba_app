package com.nimba.amortizationschedule

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One row of the latest imported amortization schedule, exposed for documents
 * that reprint the échéancier verbatim (the FA's §3.5 simulation du
 * financement). [numeroEcheance] may be the literal "VR"; [dateEcheance] is
 * null on that line. Ordered as imported (échéances first, VR last).
 */
data class ScheduleLineInfo(
    val numeroEcheance: String,
    val dateEcheance: LocalDate?,
    val loyerHt: BigDecimal,
    val taxes: BigDecimal,
    val loyerTtc: BigDecimal,
    val interet: BigDecimal,
    val capital: BigDecimal,
    val capitalRestantDu: BigDecimal?,
)
