package com.nimba.identity.internal

import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.UserInfo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class IdentityModuleApiService(
    private val users: UserRepository,
) : IdentityModuleApi {
    @Transactional(readOnly = true)
    override fun findUser(userId: UUID): UserInfo? = users.findById(userId).map { it.toUserInfo() }.orElse(null)
}

internal fun User.toUserInfo(): UserInfo =
    UserInfo(
        id = requireNotNull(id),
        fullName = fullName,
        email = email,
        role = role.name,
    )
