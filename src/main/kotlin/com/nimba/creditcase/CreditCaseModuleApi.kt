package com.nimba.creditcase

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The credit-case module's public API. Other modules create and read cases through
 * this interface only — never through the repository or entity, which are internal
 * to the module. The module's own web layer goes through this facade too, so every
 * consumer sees one behavior.
 */
interface CreditCaseModuleApi {
    fun createCase(command: CreateCreditCaseCommand): CreditCaseInfo

    /**
     * Pages through the cases (the dashboard list). [archived] narrows to archived
     * (true) or active (false) cases; null returns everything.
     */
    fun list(
        pageable: Pageable,
        archived: Boolean? = null,
    ): Page<CreditCaseInfo>

    /** Updates a case's general information (client, product, currency); 404 if unknown. */
    fun updateCase(
        id: UUID,
        command: UpdateCreditCaseCommand,
    ): CreditCaseInfo

    fun findById(id: UUID): CreditCaseInfo?

    fun findByCaseNumber(caseNumber: String): CreditCaseInfo?

    /** Flips the case status to TRADES_GENERES once trades have been generated for it. */
    fun markTradesGenerated(creditCaseId: UUID)

    /** Archives a case (hidden from the active list, nothing destroyed); 404 if unknown. */
    fun archive(id: UUID): CreditCaseInfo

    /** Puts an archived case back into the active list; 404 if unknown. */
    fun unarchive(id: UUID): CreditCaseInfo

    /**
     * Definitively deletes a case and publishes [CreditCaseDeleted] in the same
     * transaction, so dependent modules purge their attached data atomically.
     * 404 if unknown.
     */
    fun delete(id: UUID)
}

/**
 * Resolves a case or fails with the module's canonical 404. Part of the public
 * API so every consumer (this module's web layer and dependent modules alike)
 * rejects an unknown case identically.
 */
fun CreditCaseModuleApi.getOrThrow(id: UUID): CreditCaseInfo =
    findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")
