package com.nimba.caution.internal

import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionModuleApi
import com.nimba.caution.CautionStatus
import com.nimba.caution.getOrThrow
import com.nimba.shared.CurrentUser
import com.nimba.shared.PageResponse
import com.nimba.shared.toPageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/cautions")
class CautionController(
    private val cautions: CautionModuleApi,
    private val docxExport: CautionDocxExportService,
    private val currentUser: CurrentUser,
) {
    /** The generic document engine's metadata — the frontend's dynamic form is built from this, never hardcoded per type. */
    @GetMapping("/document-types")
    fun documentTypes(): List<CautionDocumentTypeResponse> = documentTypeResponses()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateCautionRequest,
    ): CautionResponse = cautions.create(request.toCommand(currentUser.id())).toResponse()

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCautionRequest,
    ): CautionResponse = cautions.update(id, request.toCommand()).toResponse()

    @PostMapping("/{id}/finalize")
    fun finalize(
        @PathVariable id: UUID,
    ): CautionResponse = cautions.finalize(id).toResponse()

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): CautionResponse = cautions.getOrThrow(id).toResponse()

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @RequestParam(required = false) clientId: UUID?,
        @RequestParam(required = false) documentType: CautionDocumentType?,
        @RequestParam(required = false) status: CautionStatus?,
    ): PageResponse<CautionResponse> = cautions.list(pageable, clientId, documentType, status).toPageResponse { it.toResponse() }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = cautions.delete(id)

    /** Only reachable once the caution is FINAL — a draft has no client snapshot to print. */
    @GetMapping("/{id}/export/docx")
    fun exportDocx(
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> {
        val export = docxExport.export(id)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .body(export.content)
    }
}
