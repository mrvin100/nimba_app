package com.nimba.identity.internal

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Orchestrates session login, logout, and current-user resolution. On success the
 * authentication is written into the session-backed [SecurityContextRepository]
 * so the session cookie carries it on subsequent requests. Any authentication
 * failure is collapsed into one generic 401 so a caller cannot tell whether the
 * email exists.
 */
@Service
class AuthService(
    private val authenticationManager: AuthenticationManager,
    private val securityContextRepository: SecurityContextRepository,
    private val loginRateLimiter: LoginRateLimiter,
) {
    fun login(
        request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): MeResponse {
        val clientIp = httpRequest.remoteAddr
        loginRateLimiter.assertNotLocked(request.email, clientIp)

        val authentication =
            try {
                authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken(request.email, request.password),
                )
            } catch (ex: AuthenticationException) {
                loginRateLimiter.recordFailure(request.email, clientIp)
                // Do NOT attach `ex` as the cause: Spring Security's
                // ExceptionTranslationFilter unwraps the cause chain, and finding an
                // AuthenticationException there would hijack the response through its
                // entry point, masking this status. A generic message keeps the
                // failure opaque (no hint whether the email exists).
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides")
            }

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = authentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, httpRequest, httpResponse)

        loginRateLimiter.recordSuccess(request.email)
        return (authentication.principal as AnalystUserDetails).toMeResponse()
    }

    fun logout(
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        httpRequest.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
    }

    fun me(authentication: Authentication): MeResponse = (authentication.principal as AnalystUserDetails).toMeResponse()
}

internal fun AnalystUserDetails.toMeResponse(): MeResponse =
    MeResponse(
        userId = userId.toString(),
        fullName = fullName,
        email = username,
        role = role.name,
    )
