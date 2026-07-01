package com.nimba.identity.internal

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Public organisation identity, used by unauthenticated screens (login) and the shell. */
data class PublicOrganizationResponse(
    val organizationName: String,
    val hasLogo: Boolean,
)

/**
 * Public read of the organisation identity (NIMBA-37). Exposes only the name and
 * whether a logo exists (no sender details), plus the logo image itself, so the login
 * page and workspace chrome reflect the configured organisation everywhere. Permitted
 * in the security config.
 */
@RestController
@RequestMapping("/auth/organization")
class PublicOrganizationController(
    private val organization: OrganizationSettingsService,
    private val logos: OrganizationLogoService,
) {
    @GetMapping
    fun get(): PublicOrganizationResponse = organization.get().let { PublicOrganizationResponse(it.organizationName, it.logoKey != null) }

    @GetMapping("/logo")
    fun logo(): ResponseEntity<ByteArray> {
        val logo = logos.read()
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(logo.contentType))
            .cacheControl(CacheControl.noCache())
            .body(logo.bytes)
    }
}
