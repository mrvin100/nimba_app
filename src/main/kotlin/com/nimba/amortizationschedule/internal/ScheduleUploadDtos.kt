package com.nimba.amortizationschedule.internal

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.util.UUID

/** Result of a successful definitive upload (201). */
data class UploadResponse(
    val id: UUID,
    val versionNumber: Int,
    val originalFilename: String,
    val uploadedAt: Instant,
    val lineCount: Int,
    val ordinaryOffsetMonths: Int,
    val vrOffsetMonths: Int,
    val fixedDayOfMonth: Int,
)

/** Body returned when an upload is rejected for parse/consistency errors (422). */
data class UploadRejectedResponse(
    val valid: Boolean,
    val errors: List<ScheduleErrorDto>,
)

/**
 * Raised when a definitive upload fails validation. Carries the same error list as
 * the preview so the frontend can show exactly what is wrong; mapped to 422 so no
 * schedule is ever persisted in error.
 */
class ScheduleValidationException(
    val errors: List<ScheduleErrorDto>,
) : RuntimeException("Le tableau d'amortissement contient des erreurs.")

@RestControllerAdvice
class ScheduleUploadExceptionHandler {
    @ExceptionHandler(ScheduleValidationException::class)
    fun handle(ex: ScheduleValidationException): ResponseEntity<UploadRejectedResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(UploadRejectedResponse(false, ex.errors))
}

internal fun AmortizationSchedule.toUploadResponse() =
    UploadResponse(
        id = requireNotNull(id),
        versionNumber = versionNumber,
        originalFilename = originalFilename,
        uploadedAt = uploadedAt,
        lineCount = lines.size,
        ordinaryOffsetMonths = ordinaryOffsetMonths,
        vrOffsetMonths = vrOffsetMonths,
        fixedDayOfMonth = fixedDayOfMonth,
    )

internal fun ParsedScheduleLine.toEntity() =
    AmortizationScheduleLine(
        numeroEcheance = numeroEcheance,
        dateEcheance = dateEcheance,
        interet = interet,
        equipement = equipement,
        assurance = assurance,
        tracking = tracking,
        immatriculation = immatriculation,
        capital = capital,
        loyerHt = loyerHt,
        taxes = taxes,
        loyerTtc = loyerTtc,
        capitalRestantDu = capitalRestantDu,
    )
