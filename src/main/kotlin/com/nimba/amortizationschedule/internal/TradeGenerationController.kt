package com.nimba.amortizationschedule.internal

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Trade generation (NIMBA-23) and consultation (NIMBA-26) for a case. Generation
 * is a single deliberate action with no hierarchical validation; consultation
 * returns only the active generation and is safe to call at any time.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/amortization-schedule/trades")
class TradeGenerationController(
    private val tradeGeneration: TradeGenerationService,
    private val tradeQuery: TradeQueryService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(
        @PathVariable caseId: UUID,
    ): List<TradeResponse> = tradeGeneration.generate(caseId).map { it.toResponse() }

    @GetMapping
    fun list(
        @PathVariable caseId: UUID,
    ): List<TradeResponse> = tradeQuery.activeTrades(caseId).map { it.toResponse() }
}
