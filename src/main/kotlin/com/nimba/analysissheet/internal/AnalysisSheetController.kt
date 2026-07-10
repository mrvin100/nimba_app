package com.nimba.analysissheet.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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

    @PutMapping
    fun update(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: AnalysisSheetContentRequest,
    ): AnalysisSheetResponse = sheets.updateDraft(caseId, request.content).toResponse(amortizationSchedules.scheduleSummary(caseId))

    @PostMapping("/publish")
    fun publish(
        @PathVariable caseId: UUID,
    ): AnalysisSheetResponse = sheets.publish(caseId).toResponse(amortizationSchedules.scheduleSummary(caseId))
}
