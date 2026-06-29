package com.nimba.amortizationschedule.internal

import java.math.BigDecimal
import java.time.LocalDate

/** One row of the preview table, values transcribed as parsed (raw, for display). */
data class PreviewLineDto(
    val lineNumber: Long,
    val numeroEcheance: String,
    val residualValue: Boolean,
    val dateEcheance: LocalDate?,
    val interet: BigDecimal,
    val equipement: BigDecimal,
    val assurance: BigDecimal,
    val tracking: BigDecimal,
    val immatriculation: BigDecimal,
    val capital: BigDecimal,
    val loyerHt: BigDecimal,
    val taxes: BigDecimal,
    val loyerTtc: BigDecimal,
    val capitalRestantDu: BigDecimal?,
)

data class ScheduleErrorDto(
    val lineNumber: Long?,
    val column: String?,
    val message: String,
)

/**
 * Preview response. The structure mirrors the display order (rows, then errors
 * tied to lines, then the informative total) so the frontend can render it without
 * transformation (NIMBA-21).
 */
data class PreviewResponse(
    val valid: Boolean,
    val lines: List<PreviewLineDto>,
    val errors: List<ScheduleErrorDto>,
    val totalLoyerTtcExcludingVr: BigDecimal,
)

internal fun ParsedScheduleLine.toPreviewDto() =
    PreviewLineDto(
        lineNumber = lineNumber,
        numeroEcheance = numeroEcheance,
        residualValue = isResidualValue,
        dateEcheance = dateEcheance,
        interet = interet,
        equipement = equipement,
        assurance = assurance,
        tracking = tracking,
        immatriculation = immatriculation,
        capital = capital,
        loyerHt = loyerHt,
        taxes = taxes,
        loyerTtc = loyerTtc,
        capitalRestantDu = capitalRestantDu,
    )

internal fun ScheduleError.toDto() = ScheduleErrorDto(lineNumber, column, message)

internal fun ScheduleValidation.toPreviewResponse() =
    PreviewResponse(
        valid = valid,
        lines = lines.map { it.toPreviewDto() },
        errors = errors.map { it.toDto() },
        totalLoyerTtcExcludingVr = totalLoyerTtcExcludingVr,
    )
