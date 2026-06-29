package com.nimba.amortizationschedule.internal

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Definitive upload of an amortization schedule (NIMBA-20). Optional offset
 * parameters override the per-dossier defaults (ordinary 1 month, VR 2 months,
 * fixed day 5). Returns 201 with the created version, or 422 (via the upload
 * exception handler) if the file has any parse or consistency error — in which
 * case nothing is persisted.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/amortization-schedule")
class ScheduleUploadController(
    private val uploadService: ScheduleUploadService,
) {
    @PostMapping(consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @PathVariable caseId: UUID,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) ordinaryOffsetMonths: Int?,
        @RequestParam(required = false) vrOffsetMonths: Int?,
        @RequestParam(required = false) fixedDayOfMonth: Int?,
    ): UploadResponse {
        val ordinary = requireInRange(ordinaryOffsetMonths ?: 1, 0, 6, "ordinaryOffsetMonths")
        val vr = requireInRange(vrOffsetMonths ?: 2, 0, 6, "vrOffsetMonths")
        val day = requireInRange(fixedDayOfMonth ?: 5, 1, 31, "fixedDayOfMonth")

        val saved =
            uploadService.upload(
                creditCaseId = caseId,
                bytes = file.bytes,
                originalFilename = file.originalFilename ?: "echeancier.csv",
                ordinaryOffsetMonths = ordinary,
                vrOffsetMonths = vr,
                fixedDayOfMonth = day,
            )
        return saved.toUploadResponse()
    }

    private fun requireInRange(
        value: Int,
        min: Int,
        max: Int,
        name: String,
    ): Int {
        if (value !in min..max) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$name doit être compris entre $min et $max (reçu : $value)")
        }
        return value
    }
}
