package com.nimba.amortizationschedule.internal

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** A generated trade, as returned to the client. */
data class TradeResponse(
    val id: UUID,
    val numeroEcheance: String,
    val dueDate: LocalDate,
    val amount: BigDecimal,
    val amountInWords: String,
    val currency: String,
)

internal fun Trade.toResponse() =
    TradeResponse(
        id = requireNotNull(id),
        numeroEcheance = numeroEcheance,
        dueDate = dueDate,
        amount = amount,
        amountInWords = amountInWords,
        currency = currency,
    )
