package com.nimba.amortizationschedule.internal

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * Trade generation (NIMBA-23), consultation (NIMBA-26), and CSV export (NIMBA-27)
 * for a case. Generation is a single deliberate action with no hierarchical
 * validation; consultation and export return only the active generation.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/amortization-schedule/trades")
class TradeGenerationController(
    private val tradeGeneration: TradeGenerationService,
    private val tradeQuery: TradeQueryService,
    private val tradeExport: TradeExportService,
    private val tradeDocxExport: TradeDocxExportService,
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

    @GetMapping("/export")
    fun export(
        @PathVariable caseId: UUID,
    ): ResponseEntity<ByteArray> {
        val export = tradeExport.export(caseId)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(export.content)
    }

    @GetMapping("/export/docx")
    fun exportDocx(
        @PathVariable caseId: UUID,
        // Signature date printed on every traité's acceptance line; the analyst
        // may pick it at download time, the download day being the default.
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) signatureDate: LocalDate?,
    ): ResponseEntity<ByteArray> {
        val export = tradeDocxExport.export(caseId, signatureDate)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .body(export.content)
    }
}
