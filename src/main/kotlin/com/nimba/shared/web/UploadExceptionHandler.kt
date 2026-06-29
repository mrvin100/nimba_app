package com.nimba.shared.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MaxUploadSizeExceededException

/**
 * Maps an over-sized multipart upload to a clear 413 instead of a generic 500, so
 * an oversized schedule CSV is rejected explicitly (NIMBA-19). The size limit
 * itself is configured via spring.servlet.multipart in application.yaml.
 */
@RestControllerAdvice
class UploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge(ex: MaxUploadSizeExceededException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "Le fichier est trop volumineux. La taille maximale autorisée est de 2 Mo.",
        )
}
