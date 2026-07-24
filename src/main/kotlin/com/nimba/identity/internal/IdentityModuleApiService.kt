package com.nimba.identity.internal

import com.nimba.identity.Department
import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.OrganizationLogo
import com.nimba.identity.OrganizationSignatories
import com.nimba.identity.UserInfo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IdentityModuleApiService(
    private val users: UserRepository,
    private val logos: OrganizationLogoService,
    private val organizationSettings: OrganizationSettingsService,
) : IdentityModuleApi {
    @Transactional(readOnly = true)
    override fun findUser(userId: UUID): UserInfo? = users.findById(userId).map { it.toUserInfo() }.orElse(null)

    @Transactional(readOnly = true)
    override fun departmentsOf(userId: UUID): Set<Department> =
        users.findById(userId).map { user -> user.memberships.map { it.department }.toSet() }.orElse(emptySet())

    @Transactional(readOnly = true)
    override fun membersOf(department: Department): List<UserInfo> =
        users.findByMembershipDepartments(listOf(department)).map { it.toUserInfo() }

    @Transactional(readOnly = true)
    override fun organizationLogo(): OrganizationLogo? = logos.find()?.let { OrganizationLogo(it.bytes, it.contentType) }

    @Transactional(readOnly = true)
    override fun organizationSignatories(): OrganizationSignatories =
        organizationSettings.get().let {
            OrganizationSignatories(
                signataire1Nom = it.signataire1Nom,
                signataire1Titre = it.signataire1Titre,
                signataire2Nom = it.signataire2Nom,
                signataire2Titre = it.signataire2Titre,
            )
        }
}

internal fun User.toUserInfo(): UserInfo =
    UserInfo(
        id = requireNotNull(id),
        fullName = fullName,
        email = email,
    )
