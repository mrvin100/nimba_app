package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import com.nimba.shared.AuthenticatedUser
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.UUID

/**
 * Spring Security principal for an authenticated platform user. Carries the fields
 * `/auth/me` needs without a second lookup, and derives authorities from the user's
 * memberships (`ROLE_{DEPARTMENT}_{ROLE}`) plus `ROLE_ADMIN` when a platform admin.
 * The account status gates authentication: a suspended account is locked, a revoked
 * account is disabled — Spring rejects both at login. Implements [AuthenticatedUser]
 * so other modules read the current user's id without crossing the identity boundary.
 */
class AnalystUserDetails(
    override val userId: UUID,
    val fullName: String,
    val memberships: Set<Membership>,
    val platformAdmin: Boolean,
    val status: AccountStatus,
    private val email: String,
    private val passwordHash: String,
) : UserDetails,
    AuthenticatedUser {
    override fun getAuthorities(): Collection<GrantedAuthority> {
        val authorities = memberships.map { SimpleGrantedAuthority("ROLE_${it.department}_${it.role}") }.toMutableList<GrantedAuthority>()
        if (platformAdmin) authorities.add(SimpleGrantedAuthority("ROLE_ADMIN"))
        return authorities
    }

    override fun getPassword(): String = passwordHash

    override fun getUsername(): String = email

    override fun isEnabled(): Boolean = status != AccountStatus.REVOKED

    override fun isAccountNonLocked(): Boolean = status != AccountStatus.SUSPENDED
}
