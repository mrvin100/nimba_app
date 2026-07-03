package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import com.nimba.shared.CurrentUser
import com.nimba.shared.getOrThrow
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Administrative user management (NIMBA-32/35). Reachable only by a platform admin
 * (ROLE_ADMIN, enforced in the security config). Owns account creation (accounts
 * are provisioned without a password and activated through an invitation), the
 * membership/admin assignment, and the lifecycle transitions (suspend, reactivate,
 * revoke). An admin cannot lock themselves out (self suspend/revoke is refused).
 */
@Service
class AdminUserService(
    private val users: UserRepository,
    private val invitations: InvitationService,
    private val currentUser: CurrentUser,
) {
    @Transactional(readOnly = true)
    fun list(pageable: Pageable): Page<AdminUserResponse> = users.findAll(pageable).map { it.toAdminResponse() }

    @Transactional
    fun create(request: CreateUserRequest): AdminUserResponse {
        val user = User(fullName = request.fullName, email = request.email)
        user.platformAdmin = request.admin
        request.memberships.forEach { user.assign(it.department, it.role) }
        val saved =
            try {
                users.saveAndFlush(user)
            } catch (ex: DataIntegrityViolationException) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cette adresse e-mail", ex)
            }
        invitations.invite(saved)
        return saved.toAdminResponse()
    }

    @Transactional
    fun changeStatus(
        id: UUID,
        status: AccountStatus,
    ): AdminUserResponse {
        if (status != AccountStatus.ACTIVE && id == currentUser.id()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Vous ne pouvez pas suspendre ou révoquer votre propre compte")
        }
        val user = users.getOrThrow(id, USER_NOT_FOUND)
        user.status = status
        return user.toAdminResponse()
    }

    @Transactional
    fun updateMemberships(
        id: UUID,
        request: UpdateMembershipsRequest,
    ): AdminUserResponse {
        val user = users.getOrThrow(id, USER_NOT_FOUND)
        user.platformAdmin = request.admin
        user.memberships.clear()
        request.memberships.forEach { user.assign(it.department, it.role) }
        return user.toAdminResponse()
    }

    companion object {
        private const val USER_NOT_FOUND = "Utilisateur introuvable"
    }
}
