package com.nimba.identity.internal

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Loads a DRI analyst by email for Spring Security's authentication provider. The
 * exception message is deliberately generic and never reveals whether the email
 * exists — the controller maps any authentication failure to a single "invalid
 * credentials" response.
 */
@Service
class AnalystUserDetailsService(
    private val users: UserRepository,
) : UserDetailsService {
    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val user = users.findByEmail(username) ?: throw UsernameNotFoundException("Invalid credentials")
        return AnalystUserDetails(
            userId = requireNotNull(user.id),
            fullName = user.fullName,
            role = user.role,
            email = user.email,
            passwordHash = user.passwordHash,
        )
    }
}
