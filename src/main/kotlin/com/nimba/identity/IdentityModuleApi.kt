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

    /**
     * The directions a user belongs to (any role). The workflow module uses this to
     * check an actor may act on a dossier at its current review stage.
     */
    fun departmentsOf(userId: UUID): Set<Department>

    /** Every active member of a direction (any role) — who the notification module fans out to. */
    fun membersOf(department: Department): List<UserInfo>

    /** The configured organisation logo, or null when none has been uploaded. */
    fun organizationLogo(): OrganizationLogo?
}
