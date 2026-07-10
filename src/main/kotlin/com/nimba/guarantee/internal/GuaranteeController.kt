package com.nimba.guarantee.internal

import com.nimba.guarantee.CreateGuaranteeCommand
import com.nimba.guarantee.GuaranteeModuleApi
import com.nimba.guarantee.UpdateGuaranteeCommand
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
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
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * A dossier's guarantees — DRI-only mutations (falls under the general
 * credit-cases matcher); every review direction can read and download files (the
 * GET matcher). [requireOwned] guards every nested path against a guarantee id
 * that exists but belongs to a different case.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/guarantees")
class GuaranteeController(
    private val guarantees: GuaranteeModuleApi,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: GuaranteeWriteRequest,
    ): GuaranteeResponse =
        guarantees
            .create(CreateGuaranteeCommand(caseId, request.kind, request.description, currentUser.id()))
            .toResponse()

    @GetMapping
    fun list(
        @PathVariable caseId: UUID,
    ): List<GuaranteeResponse> = guarantees.listByCase(caseId).map { it.toResponse() }

    @PutMapping("/{id}")
    fun update(
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
        @Valid @RequestBody request: GuaranteeWriteRequest,
    ): GuaranteeResponse =
        guarantees.update(requireOwned(caseId, id), UpdateGuaranteeCommand(request.kind, request.description)).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
    ) = guarantees.delete(requireOwned(caseId, id))

    @PostMapping("/{id}/attachments", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
    ): GuaranteeResponse {
        val guaranteeId = requireOwned(caseId, id)
        if (file.isEmpty) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier est vide")
        return guarantees
            .addAttachment(
                guaranteeId,
                file.originalFilename ?: "fichier",
                file.contentType ?: "application/octet-stream",
                file.bytes,
                currentUser.id(),
            ).toResponse()
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    fun removeAttachment(
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
        @PathVariable attachmentId: UUID,
    ): GuaranteeResponse = guarantees.removeAttachment(requireOwned(caseId, id), attachmentId).toResponse()

    @GetMapping("/{id}/attachments/{attachmentId}")
    fun download(
        @PathVariable caseId: UUID,
        @PathVariable id: UUID,
        @PathVariable attachmentId: UUID,
    ): ResponseEntity<ByteArray> {
        val guaranteeId = requireOwned(caseId, id)
        val file = guarantees.readAttachment(guaranteeId, attachmentId)
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.fileName}\"")
            .body(file.bytes)
    }

    private fun requireOwned(
        caseId: UUID,
        guaranteeId: UUID,
    ): UUID {
        val guarantee =
            guarantees.findById(guaranteeId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Garantie introuvable")
        if (guarantee.creditCaseId != caseId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Garantie introuvable")
        }
        return guaranteeId
    }
}
