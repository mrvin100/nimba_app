package com.nimba.identity.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    /** Whether any platform administrator exists (gates the one-time bootstrap). */
    fun existsByPlatformAdminTrue(): Boolean
}
