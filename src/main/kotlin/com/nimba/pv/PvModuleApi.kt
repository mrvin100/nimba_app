package com.nimba.pv

import java.util.UUID

/**
 * The PV module's public API. Other modules — and this module's own web layer —
 * read and write a case's PV through this interface only, never through the
 * repository or entities.
 */
interface PvModuleApi {
    fun findByCase(creditCaseId: UUID): PvInfo?

    /**
     * Opens the PV in DRAFT. 409 if one already exists for the case, 409 if the
     * dossier is not APPROUVE (the comité must have approved it first).
     */
    fun create(command: CreatePvCommand): PvInfo

    /** Replaces the draft's editable fields. 409 if the PV is already FINAL. */
    fun updateDraft(
        creditCaseId: UUID,
        command: UpdatePvDraftCommand,
    ): PvInfo

    /**
     * Locks the PV, freezing a [PvSnapshot] of the dossier's current identité,
     * articulation, garanties and conditions de banque. 409 if already FINAL.
     */
    fun finalize(creditCaseId: UUID): PvInfo
}
