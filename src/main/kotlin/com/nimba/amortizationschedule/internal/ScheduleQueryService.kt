package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Read access to a case's schedule state: the latest uploaded version and whether
 * the active trades were generated from it. This is what lets the échéancier
 * screen derive its workflow states from the server ("imported, generate the
 * trades", "re-imported since the last generation, regenerate") instead of local
 * component state that a page refresh would lose. An unknown case is a 404, a
 * case without any upload too — matching the analytics endpoints' semantics.
 */
@Service
class ScheduleQueryService(
    private val schedules: AmortizationScheduleRepository,
    private val trades: TradeRepository,
    private val creditCases: CreditCaseModuleApi,
) {
    @Transactional(readOnly = true)
    fun latest(creditCaseId: UUID): LatestScheduleResponse {
        creditCases.getOrThrow(creditCaseId)
        val schedule =
            schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun échéancier importé pour ce dossier.")
        val activeTrades = trades.findByCreditCaseIdAndActiveIsTrue(creditCaseId)
        return LatestScheduleResponse(
            id = requireNotNull(schedule.id),
            versionNumber = schedule.versionNumber,
            originalFilename = schedule.originalFilename,
            uploadedAt = schedule.uploadedAt,
            lineCount = schedule.lines.size,
            ordinaryOffsetMonths = schedule.ordinaryOffsetMonths,
            vrOffsetMonths = schedule.vrOffsetMonths,
            fixedDayOfMonth = schedule.fixedDayOfMonth,
            tradesUpToDate = activeTrades.isNotEmpty() && activeTrades.all { it.scheduleId == schedule.id },
        )
    }
}
