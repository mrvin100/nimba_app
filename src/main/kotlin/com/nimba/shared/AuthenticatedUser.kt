package com.nimba.shared

import java.util.UUID

/**
 * Cross-cutting view of the authenticated principal. The identity module's
 * Spring Security principal implements this so any module can read the current
 * user's id from the security context (via [CurrentUser]) without depending on the
 * identity module's internals. Kept minimal: only the id is needed for audit
 * stamping (createdBy, uploadedBy).
 *
 * Lives in the shared module's root package so it is part of the module's exposed
 * API (sub-packages of a module are internal under Spring Modulith).
 */
interface AuthenticatedUser {
    val userId: UUID
}
