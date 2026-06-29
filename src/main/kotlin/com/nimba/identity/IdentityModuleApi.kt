package com.nimba.identity

import java.util.UUID

/**
 * The identity module's public API. Other modules resolve a DRI analyst through
 * this interface only — never through the user repository or entity, which are
 * internal to the module. Kept minimal: the credit-case and amortization-schedule
 * modules need only to look a user up for audit stamping.
 */
interface IdentityModuleApi {
    fun findUser(userId: UUID): UserInfo?
}
