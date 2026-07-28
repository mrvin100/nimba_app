package com.nimba.client.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ClientRepository : JpaRepository<Client, UUID> {
    fun findByMatricule(matricule: String): Client?

    fun existsByMatricule(matricule: String): Boolean

    /** Same check for a matricule correction: a client may keep its own matricule unchanged. */
    fun existsByMatriculeAndIdNot(
        matricule: String,
        id: UUID,
    ): Boolean

    /** codeNif is the client's national tax id — genuinely unique, unlike raisonSociale which can coincidentally match. */
    fun existsByCodeNif(codeNif: String): Boolean

    /** Same check for an update: a client may keep its own codeNif unchanged. */
    fun existsByCodeNifAndIdNot(
        codeNif: String,
        id: UUID,
    ): Boolean
}
