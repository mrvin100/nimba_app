package com.nimba.identity.internal

import com.nimba.identity.Department
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?

    fun existsByEmail(email: String): Boolean

    /** Whether any platform administrator exists (gates the one-time bootstrap). */
    fun existsByPlatformAdminTrue(): Boolean

    /** Users holding at least one membership in one of the given directions. */
    @Query("select distinct u from User u join u.memberships m where m.department in :departments")
    fun findByMembershipDepartments(
        @Param("departments") departments: Collection<Department>,
    ): List<User>
}
