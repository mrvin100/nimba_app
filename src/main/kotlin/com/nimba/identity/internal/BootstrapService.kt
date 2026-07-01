package com.nimba.identity.internal

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * One-time creation of the first platform administrator (NIMBA-35). The endpoint is
 * public but self-disables: as soon as any admin exists it refuses further calls,
 * so it can only ever create the initial admin who then provisions everyone else.
 */
@Service
class BootstrapService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional(readOnly = true)
    fun available(): Boolean = !users.existsByPlatformAdminTrue()

    @Transactional
    fun bootstrap(request: BootstrapRequest): AdminUserResponse {
        if (users.existsByPlatformAdminTrue()) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un administrateur existe déjà : l'initialisation est désactivée")
        }
        val user =
            User(
                fullName = request.fullName,
                email = request.email,
                passwordHash = passwordEncoder.encode(request.password),
            )
        user.platformAdmin = true
        return try {
            users.saveAndFlush(user).toAdminResponse()
        } catch (ex: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cette adresse e-mail", ex)
        }
    }
}
