package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Administrative user management (NIMBA-32). Reachable only by a platform admin
 * (ROLE_ADMIN, enforced in the security config). Owns account creation, the
 * membership/admin assignment, and the lifecycle transitions (suspend, reactivate,
 * revoke). Suspended/revoked accounts can no longer authenticate.
 */
@Service
class AdminUserService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional(readOnly = true)
    fun list(pageable: Pageable): Page<AdminUserResponse> = users.findAll(pageable).map { it.toAdminResponse() }

    @Transactional
    fun create(request: CreateUserRequest): AdminUserResponse {
        val user =
            User(
                fullName = request.fullName,
                email = request.email,
                passwordHash = requireNotNull(passwordEncoder.encode(request.password)),
            )
        user.platformAdmin = request.admin
        request.memberships.forEach { user.assign(it.department, it.role) }
        return try {
            users.saveAndFlush(user).toAdminResponse()
        } catch (ex: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cette adresse e-mail", ex)
        }
    }

    @Transactional
    fun changeStatus(
        id: UUID,
        status: AccountStatus,
    ): AdminUserResponse {
        val user = users.findById(id).orElseThrow { notFound() }
        user.status = status
        return user.toAdminResponse()
    }

    @Transactional
    fun updateMemberships(
        id: UUID,
        request: UpdateMembershipsRequest,
    ): AdminUserResponse {
        val user = users.findById(id).orElseThrow { notFound() }
        user.platformAdmin = request.admin
        user.memberships.clear()
        request.memberships.forEach { user.assign(it.department, it.role) }
        return user.toAdminResponse()
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable")
}
