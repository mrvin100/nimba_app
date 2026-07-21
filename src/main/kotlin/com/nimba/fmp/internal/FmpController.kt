package com.nimba.fmp.internal

import com.nimba.fmp.FmpModuleApi
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/credit-cases/{caseId}/fmp")
class FmpController(
    private val fmps: FmpModuleApi,
    private val docxExport: FmpDocxExportService,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: CreateFmpRequest,
    ): FmpResponse = fmps.create(request.toCommand(caseId, currentUser.id())).toResponse()

    @GetMapping
    fun get(
        @PathVariable caseId: UUID,
    ): FmpResponse =
        fmps.findByCase(caseId)?.toResponse()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche de mise en place pour ce dossier")

    @GetMapping("/export/docx")
    fun exportDocx(
        @PathVariable caseId: UUID,
    ): ResponseEntity<ByteArray> {
        val export = docxExport.export(caseId)
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .body(export.content)
    }
}
