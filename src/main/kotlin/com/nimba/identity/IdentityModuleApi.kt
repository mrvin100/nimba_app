package com.nimba.identity

import java.util.UUID

/**
 * The identity module's public API. Other modules resolve a DRI analyst through
 * this interface only — never through the user repository or entity, which are
 * internal to the module. Kept minimal: the credit-case and amortization-schedule
 * modules look a user up for audit stamping and read the organisation logo to print
 * it on generated documents.
 */
interface IdentityModuleApi {
    fun findUser(userId: UUID): UserInfo?

    /** The configured organisation logo, or null when none has been uploaded. */
    fun organizationLogo(): OrganizationLogo?
}
