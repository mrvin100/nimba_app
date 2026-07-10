package com.nimba.notification

import com.nimba.identity.Department
import java.util.UUID

/**
 * The notification module's public API. The workflow module calls this after every
 * transition to tell the direction whose turn it is next — this module resolves who
 * that is (via the identity module) and fans out one notification per member. Other
 * modules never write directly to the notification store.
 */
interface NotificationModuleApi {
    /** Notifies one user. */
    fun notifyUser(
        recipientId: UUID,
        creditCaseId: UUID?,
        message: String,
    )

    /** Notifies every current member of a direction (any role). */
    fun notifyDepartment(
        department: Department,
        creditCaseId: UUID?,
        message: String,
    )
}
