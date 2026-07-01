package com.nimba.identity.internal

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public organisation name, used by unauthenticated screens (login) and the shell. */
data class PublicOrganizationResponse(
    val organizationName: String,
)

/**
 * Public read of the organisation identity (NIMBA-37). Exposes only the name (no
 * sender details), so the login page and workspace chrome reflect the configured
 * organisation everywhere. Permitted in the security config.
 */
@RestController
@RequestMapping("/auth/organization")
class PublicOrganizationController(
    private val organization: OrganizationSettingsService,
) {
    @GetMapping
    fun get(): PublicOrganizationResponse = PublicOrganizationResponse(organization.get().organizationName)
}
