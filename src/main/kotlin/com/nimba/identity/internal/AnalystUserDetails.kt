package com.nimba.identity.internal

import com.nimba.shared.AuthenticatedUser
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

/**
 * Spring Security principal for an authenticated DRI analyst. Carries the fields
 * the application needs after login (id, name, role) so `/auth/me` can answer
 * without a second database lookup. The single role maps to the
 * `ROLE_DRI_ANALYST` authority. Implements [AuthenticatedUser] so other modules
 * can read the current user's id without crossing the identity boundary.
 */
class AnalystUserDetails(
    override val userId: UUID,
    val fullName: String,
    val role: UserRole,
    private val email: String,
    private val passwordHash: String,
) : UserDetails,
    AuthenticatedUser {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = email
}
