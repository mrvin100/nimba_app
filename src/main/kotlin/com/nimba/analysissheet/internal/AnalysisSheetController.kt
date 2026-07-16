package com.nimba.analysissheet.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.analysissheet.FaSectionKey
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/credit-cases/{caseId}/analysis-sheet")
class AnalysisSheetController(
    private val sheets: AnalysisSheetModuleApi,
    private val amortizationSchedules: AmortizationScheduleModuleApi,
    private val docxExport: AnalysisSheetDocxExportService,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable caseId: UUID,
    ): AnalysisSheetResponse =
        sheets
            .create(CreateAnalysisSheetCommand(creditCaseId = caseId, createdBy = currentUser.id()))
            .toResponse(amortizationSchedules.scheduleSummary(caseId))

    @GetMapping
    fun get(
        @PathVariable caseId: UUID,
    ): AnalysisSheetResponse {
        val sheet =
            sheets.findByCase(caseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche d'analyse pour ce dossier")
        return sheet.toResponse(amortizationSchedules.scheduleSummary(caseId))
    }

    @GetMapping("/sections")
    fun sections(
        @PathVariable caseId: UUID,
    ): List<FaSectionResponse> = sheets.sections(caseId).map { it.toResponse() }

    @PutMapping("/sections/{key}")
    fun updateSection(
        @PathVariable caseId: UUID,
        @PathVariable key: FaSectionKey,
        @Valid @RequestBody request: FaSectionRequest,
    ): FaSectionResponse = sheets.updateSection(caseId, key, request.contentJson).toResponse()

    @PostMapping("/publish")
    fun publish(
        @PathVariable caseId: UUID,
    ): AnalysisSheetResponse = sheets.publish(caseId).toResponse(amortizationSchedules.scheduleSummary(caseId))

    // Exports whatever the dossier currently holds — RAS for anything not yet
    // captured — so a manager can chase the client for missing information
    // whether the FA is a draft, published, or not even started yet.
    @GetMapping("/export/docx")
    fun exportDocx(
        @PathVariable caseId: UUID,
    ): ResponseEntity<ByteArray> {
        val export = docxExport.export(caseId)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .body(export.content)
    }
}
