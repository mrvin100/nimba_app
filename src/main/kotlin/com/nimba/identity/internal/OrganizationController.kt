package com.nimba.identity.internal

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Organisation settings API (NIMBA-35). Under the admin path tree, so it requires
 * ROLE_ADMIN (security config). Governs the invitation e-mail sender identity and
 * the organisation name.
 */
@RestController
@RequestMapping("/admin/organization")
class OrganizationController(
    private val organization: OrganizationSettingsService,
) {
    @GetMapping
    fun get(): OrganizationResponse = organization.get().toResponse()

    @PutMapping
    fun update(
        @Valid @RequestBody request: UpdateOrganizationRequest,
    ): OrganizationResponse = organization.update(request).toResponse()
}
