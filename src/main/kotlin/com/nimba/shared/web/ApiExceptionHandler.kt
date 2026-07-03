package com.nimba.shared.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * Cross-cutting exception mapping to RFC 7807 problem details — the one shape the
 * frontend's `ApiError` understands. Domain-specific rejections that carry their
 * own response body (e.g. the schedule-upload 422 with per-line errors) stay in
 * their module's advice; only genuinely generic mappings live here.
 */
@RestControllerAdvice
class ApiExceptionHandler {
    /**
     * Bean-validation failure on a request body. Keeps the established 400 status
     * and enriches the problem with a field → messages map so a form can surface
     * each error next to its input.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "La requête est invalide.")
        problem.setProperty(
            "errors",
            ex.bindingResult.fieldErrors.groupBy({ it.field }, { it.defaultMessage ?: "Valeur invalide" }),
        )
        return problem
    }

    /**
     * Over-sized multipart upload → a clear 413 instead of a generic 500, so an
     * oversized schedule CSV is rejected explicitly (NIMBA-19). The size limit
     * itself is configured via spring.servlet.multipart in application.yaml.
     */
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge(ex: MaxUploadSizeExceededException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Le fichier est trop volumineux. La taille maximale autorisée est de 2 Mo.",
        )
}
