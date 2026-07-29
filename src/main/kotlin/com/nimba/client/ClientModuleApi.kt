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

    /** Corrects a client's matricule (manager-gated at the controller); 404 if unknown, 409 if already taken by another client. */
    fun updateMatricule(
        id: UUID,
        command: UpdateClientMatriculeCommand,
    ): ClientInfo

    fun findById(id: UUID): ClientInfo?

    /** Batch resolve clients by id, in no particular order — lets a consumer paging a list of dossiers fetch their clients in one query. */
    fun findByIds(ids: Collection<UUID>): List<ClientInfo>

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
