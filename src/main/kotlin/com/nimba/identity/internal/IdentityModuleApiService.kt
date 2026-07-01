package com.nimba.identity.internal

import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.OrganizationLogo
import com.nimba.identity.UserInfo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IdentityModuleApiService(
    private val users: UserRepository,
    private val logos: OrganizationLogoService,
) : IdentityModuleApi {
    @Transactional(readOnly = true)
    override fun findUser(userId: UUID): UserInfo? = users.findById(userId).map { it.toUserInfo() }.orElse(null)

    @Transactional(readOnly = true)
    override fun organizationLogo(): OrganizationLogo? = logos.find()?.let { OrganizationLogo(it.bytes, it.contentType) }
}

internal fun User.toUserInfo(): UserInfo =
    UserInfo(
        id = requireNotNull(id),
        fullName = fullName,
        email = email,
    )
