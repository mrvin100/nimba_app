package com.nimba.caution.internal

import com.nimba.caution.CautionModuleApi
import com.nimba.caution.dossierOrThrow
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * A caution dossier groups every document generated for one client request
 * against one appel d'offres. Documents are attached by passing `dossierId`
 * when creating a caution; this controller opens the dossier and reads it back
 * with its documents.
 */
@RestController
@RequestMapping("/caution-dossiers")
class CautionDossierController(
    private val cautions: CautionModuleApi,
    private val docxExport: CautionDocxExportService,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateDossierRequest,
    ): DossierResponse = cautions.createDossier(request.toCommand(currentUser.id())).toResponse()

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @RequestParam(required = false) clientId: UUID?,
    ): PageResponse<DossierResponse> = cautions.listDossiers(pageable, clientId).toPageResponse { it.toResponse() }

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): DossierDetailResponse =
        DossierDetailResponse(
            dossier = cautions.dossierOrThrow(id).toResponse(),
            documents = cautions.dossierDocuments(id).map { it.toResponse() },
        )

    /** The dossier's Notification de caution as a Word (.docx) download. */
    @GetMapping("/{id}/notification/docx")
    fun exportNotification(
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> = docx(docxExport.exportDossierNotification(id))

    /** The dossier's Fiche d'approbation as a Word (.docx) download. */
    @GetMapping("/{id}/fiche/docx")
    fun exportFiche(
        @PathVariable id: UUID,
    ): ResponseEntity<ByteArray> = docx(docxExport.exportDossierFiche(id))

    private fun docx(export: CautionExport): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
            .body(export.content)
}
