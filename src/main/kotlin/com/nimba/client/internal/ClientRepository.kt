package com.nimba.client.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ClientRepository : JpaRepository<Client, UUID> {
    fun findByMatricule(matricule: String): Client?

    fun existsByMatricule(matricule: String): Boolean
}
