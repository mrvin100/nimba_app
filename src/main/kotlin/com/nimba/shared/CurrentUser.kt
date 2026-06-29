package com.nimba.shared

import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Resolves the currently authenticated analyst's id from the security context.
 * Endpoints are already protected by the security filter chain, so reaching this
 * without an authenticated [AuthenticatedUser] principal is an unexpected state and
 * surfaces as 401 rather than a null.
 */
@Component
class CurrentUser {
    fun id(): UUID {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is AuthenticatedUser) return principal.userId
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise")
    }
}
