package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Preview of an uploaded amortization-schedule CSV (NIMBA-19). Parses and runs the
 * consistency checks but writes nothing — it is a pure round trip that lets the
 * analyst verify the file before committing. Content errors are returned with a
 * 200 (they are the point of the preview); only a fundamentally unreadable file
 * (bad header, non-UTF-8, empty) yields a 400.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/amortization-schedule")
class SchedulePreviewController(
    private val validation: ScheduleValidationService,
    private val creditCases: CreditCaseModuleApi,
) {
    @PostMapping("/preview", consumes = ["multipart/form-data"])
    fun preview(
        @PathVariable caseId: UUID,
        @RequestParam("file") file: MultipartFile,
    ): PreviewResponse {
        creditCases.getOrThrow(caseId)

        val result = file.inputStream.use { validation.validate(it) }
        if (result.fatal) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                result.errors.firstOrNull()?.message ?: "Le fichier n'a pas pu être lu.",
            )
        }
        return result.toPreviewResponse()
    }
}
