package com.nimba.identity.internal

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import com.nimba.shared.CurrentUser
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Team provisioning by direction managers (NIMBA-35). A manager may invite a MEMBER
 * into a direction they manage; a platform admin may invite into any direction. The
 * invited account is created without a password and activated via the invitation.
 */
@Service
class TeamService(
    private val users: UserRepository,
    private val invitations: InvitationService,
    private val currentUser: CurrentUser,
) {
    @Transactional
    fun inviteMember(request: InviteMemberRequest): AdminUserResponse {
        val caller =
            users.findById(currentUser.id()).orElseThrow {
                ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise")
            }
        if (!caller.canInviteInto(request.department)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Seul un manager de cette direction peut inviter un membre")
        }
        val user = User(fullName = request.fullName, email = request.email)
        user.assign(request.department, DepartmentRole.MEMBER)
        val saved =
            try {
                users.saveAndFlush(user)
            } catch (ex: DataIntegrityViolationException) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe déjà avec cette adresse e-mail", ex)
            }
        invitations.invite(saved)
        return saved.toAdminResponse()
    }

    private fun User.canInviteInto(department: Department): Boolean =
        platformAdmin || memberships.any { it.department == department && it.role == DepartmentRole.MANAGER }
}
