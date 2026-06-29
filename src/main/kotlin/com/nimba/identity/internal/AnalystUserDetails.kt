package com.nimba.identity.internal

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

/**
 * Spring Security principal for an authenticated DRI analyst. Carries the fields
 * the application needs after login (id, name, role) so `/auth/me` can answer
 * without a second database lookup. The single role maps to the
 * `ROLE_DRI_ANALYST` authority.
 */
class AnalystUserDetails(
    val userId: UUID,
    val fullName: String,
    val role: UserRole,
    private val email: String,
    private val passwordHash: String,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_${role.name}"))

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = email
}
