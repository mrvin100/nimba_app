package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import com.nimba.shared.CurrentUser
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

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

    /** The members (role MEMBER) of the directions the caller manages. */
    @Transactional(readOnly = true)
    fun listMembers(): List<AdminUserResponse> {
        val caller = caller()
        val managed = caller.managedDepartments()
        if (managed.isEmpty()) return emptyList()
        return users
            .findByMembershipDepartments(managed)
            .filter { it.isManageableMemberFor(managed, caller) }
            .sortedByDescending { it.createdAt }
            .map { it.toAdminResponse() }
    }

    /** Applies a lifecycle transition to a member of a direction the caller manages. */
    @Transactional
    fun changeMemberStatus(
        id: UUID,
        status: AccountStatus,
    ): AdminUserResponse {
        val caller = caller()
        val managed = caller.managedDepartments()
        val target = users.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable") }
        if (!target.isManageableMemberFor(managed, caller)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Vous ne pouvez gérer que les membres de vos directions")
        }
        target.status = status
        return target.toAdminResponse()
    }

    private fun caller(): User =
        users.findById(currentUser.id()).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise")
        }

    private fun User.managedDepartments(): Set<Department> =
        if (platformAdmin) {
            Department.entries.toSet()
        } else {
            memberships.filter { it.role == DepartmentRole.MANAGER }.map { it.department }.toSet()
        }

    /** A non-admin member (not the caller) holding a MEMBER role in a managed direction. */
    private fun User.isManageableMemberFor(
        managed: Set<Department>,
        caller: User,
    ): Boolean =
        !platformAdmin &&
            id != caller.id &&
            memberships.any { it.department in managed && it.role == DepartmentRole.MEMBER }

    private fun User.canInviteInto(department: Department): Boolean =
        platformAdmin || memberships.any { it.department == department && it.role == DepartmentRole.MANAGER }
}
