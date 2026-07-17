package com.nimba.analysissheet.internal

import com.nimba.creditcase.CreditCaseDeleted
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Purges a case's Fiche d'analyse when an administrator deletes the case — see the
 * amortization-schedule module's own listener for why this runs on the event
 * rather than a cross-module dependency. Section images' MinIO objects are removed
 * best-effort after their rows.
 */
@Component
class AnalysisSheetPurgeListener(
    private val sheets: AnalysisSheetRepository,
    private val sections: AnalysisSheetSectionRepository,
    private val images: AnalysisSheetImageRepository,
    private val imageService: AnalysisSheetImageService,
) {
    @EventListener
    fun purge(event: CreditCaseDeleted) {
        sheets.findByCreditCaseId(event.creditCaseId)?.let { sheet ->
            val sheetId = requireNotNull(sheet.id)
            val storageKeys = images.findByAnalysisSheetIdOrderByUploadedAt(sheetId).map { it.storageKey }
            images.deleteByAnalysisSheetId(sheetId)
            sections.deleteByAnalysisSheetId(sheetId)
            imageService.deleteFiles(storageKeys)
        }
        sheets.deleteByCreditCaseId(event.creditCaseId)
    }
}
