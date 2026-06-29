package com.nimba.amortizationschedule.internal

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Triggers trade generation from the case's latest schedule (NIMBA-23). A single
 * deliberate action by the analyst, with no hierarchical validation. Returns 201
 * with all generated trades, or 409 if no schedule has been uploaded yet.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/amortization-schedule/trades")
class TradeGenerationController(
    private val tradeGeneration: TradeGenerationService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(
        @PathVariable caseId: UUID,
    ): List<TradeResponse> = tradeGeneration.generate(caseId).map { it.toResponse() }
}
