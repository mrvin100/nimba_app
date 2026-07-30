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

    /** Replaces a document's field answers and records a history version. Refused when the dossier is locked (FINALISE). */
    fun update(
        id: UUID,
        command: UpdateCautionCommand,
        actor: UUID,
    ): CautionInfo

    /** A document's edit history, newest first. */
    fun documentHistory(id: UUID): List<CautionDocumentVersionInfo>

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

    /** Opens a caution dossier (one client request against one appel d'offres) and assigns its reference number. 404 if the client is unknown. */
    fun createDossier(command: CreateDossierCommand): CautionDossierInfo

    /** Replaces a dossier's shared market/companion content and bumps its version. 404 if unknown. */
    fun updateDossier(
        id: UUID,
        content: Map<String, String>,
    ): CautionDossierInfo

    /**
     * Finalizes the client's request: freezes every document's client snapshot,
     * locks the dossier (BROUILLON → FINALISE). 409 if not in BROUILLON.
     */
    fun finalizeDossier(
        id: UUID,
        actor: UUID,
    ): CautionDossierInfo

    /**
     * Reopens a finalized dossier to correct a single document (Manager only):
     * FINALISE → EN_PROROGATION, journaling [reason] and [actor]. 409 if not FINALISE.
     */
    fun prorogeDossier(
        id: UUID,
        actor: UUID,
        reason: String,
    ): CautionDossierInfo

    /** Re-locks a prorogated dossier once the correction is done: EN_PROROGATION → FINALISE, version++. 409 if not EN_PROROGATION. */
    fun refinalizeDossier(
        id: UUID,
        actor: UUID,
    ): CautionDossierInfo

    /** A dossier's lifecycle journal, newest first. */
    fun dossierEvents(id: UUID): List<CautionDossierEventInfo>

    /** Deletes a dossier and every document attached to it. 404 if unknown. Publishes [CautionDossierDeleted]. */
    fun deleteDossier(id: UUID)

    fun findDossier(id: UUID): CautionDossierInfo?

    /** Pages through dossiers, newest first; the client filter is optional. */
    fun listDossiers(
        pageable: Pageable,
        clientId: UUID? = null,
    ): Page<CautionDossierInfo>

    /** Every document attached to a dossier, newest first. */
    fun dossierDocuments(dossierId: UUID): List<CautionInfo>
}

/** Resolves a dossier or fails with the module's canonical 404. */
fun CautionModuleApi.dossierOrThrow(id: UUID): CautionDossierInfo =
    findDossier(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")

/** Resolves a caution or fails with the module's canonical 404. */
fun CautionModuleApi.getOrThrow(id: UUID): CautionInfo =
    findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable")
