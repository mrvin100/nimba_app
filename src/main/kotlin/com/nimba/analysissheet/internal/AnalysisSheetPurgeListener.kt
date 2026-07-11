package com.nimba.analysissheet.internal

import com.nimba.creditcase.CreditCaseDeleted
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Purges a case's Fiche d'analyse when an administrator deletes the case — see the
 * amortization-schedule module's own listener for why this runs on the event
 * rather than a cross-module dependency.
 */
@Component
class AnalysisSheetPurgeListener(
    private val sheets: AnalysisSheetRepository,
    private val sections: AnalysisSheetSectionRepository,
) {
    @EventListener
    fun purge(event: CreditCaseDeleted) {
        sheets.findByCreditCaseId(event.creditCaseId)?.let { sheet ->
            sections.deleteByAnalysisSheetId(requireNotNull(sheet.id))
        }
        sheets.deleteByCreditCaseId(event.creditCaseId)
    }
}
