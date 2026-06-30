package com.nimba.identity

import java.util.UUID

/**
 * Read-only view of a user, safe to share across module boundaries. Other modules
 * use this (via [IdentityModuleApi]) to resolve who created or uploaded something
 * for audit stamping — never the [com.nimba.identity.internal] entity.
 */
data class UserInfo(
    val id: UUID,
    val fullName: String,
    val email: String,
)
