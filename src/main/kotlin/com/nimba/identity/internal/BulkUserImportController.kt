package com.nimba.identity.internal

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.multipart.MultipartFile

/**
 * Bulk user import API (NIMBA-35). Under the admin path tree (ROLE_ADMIN). Offers a
 * template download, a non-persisting preview, and an all-or-nothing commit that
 * invites every created account.
 */
@RestController
@RequestMapping("/admin/users/import")
class BulkUserImportController(
    private val bulkImport: BulkUserImportService,
) {
    @GetMapping("/template")
    fun template(): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-import-utilisateurs.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(BulkUserImportService.TEMPLATE_CSV)

    @PostMapping("/preview", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun preview(
        @RequestParam("file") file: MultipartFile,
    ): BulkPreviewResponse = bulkImport.preview(file.bytes)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun import(
        @RequestParam("file") file: MultipartFile,
    ): BulkImportResponse = bulkImport.import(file.bytes)
}

@RestControllerAdvice
class BulkImportExceptionHandler {
    @ExceptionHandler(BulkImportValidationException::class)
    fun handle(ex: BulkImportValidationException): ResponseEntity<BulkPreviewResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ex.preview)
}
