package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.shared.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * Persists a validated amortization schedule as a new version of a case (NIMBA-20).
 * Reuses the exact same validation as the preview, so the two cannot diverge. If
 * any parse or consistency error is found, nothing is persisted and the caller
 * gets a 422. The version number is the existing maximum plus one (1 for the
 * first upload); the unique (case, version) constraint guarantees integrity under
 * concurrent uploads. The original CSV bytes are retained on disk for audit.
 */
@Service
class ScheduleUploadService(
    private val validation: ScheduleValidationService,
    private val schedules: AmortizationScheduleRepository,
    private val fileStorage: ScheduleFileStorage,
    private val currentUser: CurrentUser,
    private val creditCases: CreditCaseModuleApi,
) {
    @Transactional
    fun upload(
        creditCaseId: UUID,
        bytes: ByteArray,
        originalFilename: String,
        ordinaryOffsetMonths: Int,
        vrOffsetMonths: Int,
        fixedDayOfMonth: Int,
    ): AmortizationSchedule {
        creditCases.findById(creditCaseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")

        val result = validation.validate(ByteArrayInputStream(bytes))
        if (!result.valid) {
            throw ScheduleValidationException(result.errors.map { it.toDto() })
        }

        val nextVersion = (schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(creditCaseId)?.versionNumber ?: 0) + 1
        val schedule =
            AmortizationSchedule(
                creditCaseId = creditCaseId,
                versionNumber = nextVersion,
                originalFilename = originalFilename,
                uploadedBy = currentUser.id(),
                ordinaryOffsetMonths = ordinaryOffsetMonths,
                vrOffsetMonths = vrOffsetMonths,
                fixedDayOfMonth = fixedDayOfMonth,
            )
        result.lines.forEach { schedule.addLine(it.toEntity()) }
        val saved = schedules.save(schedule)

        fileStorage.store(creditCaseId, nextVersion, bytes)
        return saved
    }
}
