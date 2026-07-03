package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.RoundingMode
import java.util.UUID

/**
 * Generates the trades for a case from its most recently uploaded schedule
 * (NIMBA-23). Orchestrates the already-tested due-date calculator and
 * amount-to-words converter; it does not reimplement them. The whole generation is
 * one transaction (all trades or none). Any previously active generation is marked
 * superseded first, so a case has exactly one active set of trades at a time
 * (NIMBA-24) — old trades stay in the database for traceability. Each line yields
 * one trade (ordinary lines then the VR), matching the cardinality rule.
 */
@Service
class TradeGenerationService(
    private val schedules: AmortizationScheduleRepository,
    private val trades: TradeRepository,
    private val dueDateCalculator: TradeDueDateCalculator,
    private val amountToWords: AmountToFrenchWordsConverter,
    private val creditCases: CreditCaseModuleApi,
) {
    @Transactional
    fun generate(creditCaseId: UUID): List<Trade> {
        val case =
            creditCases.getOrThrow(creditCaseId)
        val schedule =
            schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(creditCaseId)
                ?: throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Aucun tableau d'amortissement n'a été téléversé pour ce dossier. Téléversez-en un avant de générer les trades.",
                )

        // Supersede any currently active trades so only this generation stays active.
        val previouslyActive = trades.findByCreditCaseIdAndActiveIsTrue(creditCaseId)
        previouslyActive.forEach { it.active = false }
        trades.saveAll(previouslyActive)

        val lastEcheanceDate =
            schedule.lines
                .filterNot { it.isResidualValue }
                .mapNotNull { it.dateEcheance }
                .maxOrNull()

        val generated =
            schedule.lines.map { line ->
                val baseDate =
                    if (line.isResidualValue) {
                        lastEcheanceDate
                            ?: throw ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "Impossible de dater la ligne VR : aucune échéance ordinaire datée.",
                            )
                    } else {
                        line.dateEcheance
                            ?: throw ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "Échéance ${line.numeroEcheance} sans date.",
                            )
                    }
                val offsetMonths = if (line.isResidualValue) schedule.vrOffsetMonths else schedule.ordinaryOffsetMonths
                val dueDate = dueDateCalculator.dueDate(baseDate, offsetMonths, schedule.fixedDayOfMonth)
                val amount = line.loyerTtc.setScale(0, RoundingMode.HALF_UP)

                Trade(
                    creditCaseId = creditCaseId,
                    scheduleId = requireNotNull(schedule.id),
                    sourceLineId = requireNotNull(line.id),
                    numeroEcheance = line.numeroEcheance,
                    dueDate = dueDate,
                    amount = amount,
                    amountInWords = amountToWords.convert(amount, case.currency),
                    currency = case.currency,
                )
            }

        val saved = trades.saveAll(generated)
        creditCases.markTradesGenerated(creditCaseId)
        return saved
    }
}
