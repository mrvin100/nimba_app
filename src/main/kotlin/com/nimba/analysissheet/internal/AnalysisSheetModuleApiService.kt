package com.nimba.analysissheet.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.analysissheet.AnalysisSheetInfo
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.creditcase.CaseTypePolicies
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class AnalysisSheetModuleApiService(
    private val sheets: AnalysisSheetRepository,
    private val creditCases: CreditCaseModuleApi,
    private val amortizationSchedules: AmortizationScheduleModuleApi,
) : AnalysisSheetModuleApi {
    @Transactional(readOnly = true)
    override fun findByCase(creditCaseId: UUID): AnalysisSheetInfo? = sheets.findByCreditCaseId(creditCaseId)?.toInfo()

    @Transactional
    override fun create(command: CreateAnalysisSheetCommand): AnalysisSheetInfo {
        val case = creditCases.getOrThrow(command.creditCaseId)
        if (!amortizationSchedules.hasScheduleForCase(command.creditCaseId)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Le tableau d'amortissement doit être importé avant d'initier la fiche d'analyse",
            )
        }
        if (sheets.existsByCreditCaseId(command.creditCaseId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Une fiche d'analyse existe déjà pour ce dossier")
        }
        // The case's type was already validated at creation (CaseTypePolicies covers
        // every persisted combination), so this always resolves.
        val policy = requireNotNull(CaseTypePolicies.find(case.productType, case.contractType))

        val saved =
            sheets.save(
                AnalysisSheet(
                    creditCaseId = command.creditCaseId,
                    faVariant = policy.faVariant,
                    createdBy = command.createdBy,
                ),
            )
        return saved.toInfo()
    }

    @Transactional
    override fun updateDraft(
        creditCaseId: UUID,
        content: String?,
    ): AnalysisSheetInfo {
        val sheet = requireDraft(creditCaseId)
        sheet.content = content
        sheet.updatedAt = Instant.now()
        return sheet.toInfo()
    }

    @Transactional
    override fun publish(creditCaseId: UUID): AnalysisSheetInfo {
        val sheet = requireDraft(creditCaseId)
        sheet.status = AnalysisSheetStatus.PUBLISHED
        sheet.publishedAt = Instant.now()
        sheet.updatedAt = sheet.publishedAt!!
        return sheet.toInfo()
    }

    @Transactional
    override fun reopen(creditCaseId: UUID) {
        val sheet = sheets.findByCreditCaseId(creditCaseId) ?: return
        if (sheet.status == AnalysisSheetStatus.PUBLISHED) {
            sheet.status = AnalysisSheetStatus.DRAFT
            sheet.publishedAt = null
            sheet.updatedAt = Instant.now()
        }
    }

    private fun requireDraft(creditCaseId: UUID): AnalysisSheet {
        val sheet =
            sheets.findByCreditCaseId(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche d'analyse pour ce dossier")
        if (sheet.status != AnalysisSheetStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La fiche d'analyse est déjà publiée")
        }
        return sheet
    }
}

internal fun AnalysisSheet.toInfo(): AnalysisSheetInfo =
    AnalysisSheetInfo(
        id = requireNotNull(id),
        creditCaseId = creditCaseId,
        faVariant = faVariant,
        status = status,
        content = content,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
        publishedAt = publishedAt,
    )
