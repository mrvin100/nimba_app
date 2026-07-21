package com.nimba.client

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The client module's public API. Other modules (the Caution module first)
 * read and write client records through this interface only — never through
 * the repository or entity, which are internal to the module.
 */
interface ClientModuleApi {
    /** 409 if [CreateClientCommand.matricule] is already taken. */
    fun create(command: CreateClientCommand): ClientInfo

    /** Updates a client's descriptive details; 404 if unknown. */
    fun update(
        id: UUID,
        command: UpdateClientCommand,
    ): ClientInfo

    fun findById(id: UUID): ClientInfo?

    fun findByMatricule(matricule: String): ClientInfo?

    /** Pages through every client, newest first. */
    fun list(pageable: Pageable): Page<ClientInfo>
}

/**
 * Resolves a client or fails with the module's canonical 404. Part of the
 * public API so every consumer rejects an unknown client identically.
 */
fun ClientModuleApi.getOrThrow(id: UUID): ClientInfo =
    findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable")
