package com.nimba.caution

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The Caution module's public API. Other modules — and this module's own web
 * layer — read and write a caution through this interface only, never
 * through the repository or entity.
 */
interface CautionModuleApi {
    /**
     * Opens a caution in DRAFT for an existing client and assigns its
     * reference number. 404 if the client is unknown; 400 if [CreateCautionCommand.content]
     * is missing a required field for [CreateCautionCommand.documentType]
     * (see `CautionFieldRegistry`).
     */
    fun create(command: CreateCautionCommand): CautionInfo

    /** Replaces a draft's field answers; 409 once the caution is FINAL. */
    fun update(
        id: UUID,
        command: UpdateCautionCommand,
    ): CautionInfo

    /** Locks the caution, freezing a snapshot of the issuing client's identity. 409 if already FINAL. */
    fun finalize(id: UUID): CautionInfo

    fun findById(id: UUID): CautionInfo?

    /** Pages through cautions, newest first; every filter is optional. */
    fun list(
        pageable: Pageable,
        clientId: UUID? = null,
        documentType: CautionDocumentType? = null,
        status: CautionStatus? = null,
    ): Page<CautionInfo>

    /** Deletes a draft caution (never a finalized one — it is an official record). 409 if FINAL. */
    fun delete(id: UUID)

    /** Whether any caution has ever been created — drives whether the create form still offers a starting-sequence override. */
    fun referenceSequenceInitialized(): Boolean
}

/** Resolves a caution or fails with the module's canonical 404. */
fun CautionModuleApi.getOrThrow(id: UUID): CautionInfo =
    findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable")
