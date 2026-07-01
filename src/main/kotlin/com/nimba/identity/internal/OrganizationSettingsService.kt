package com.nimba.identity.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

/**
 * Reads and updates the single organisation-settings row (NIMBA-35). The sender
 * identity here is what invitation e-mails are sent from. The logo image itself is
 * handled by [OrganizationLogoService] (object-storage I/O); this service owns the
 * settings row (name, sender, and the logo key/content-type persisted on it).
 */
@Service
class OrganizationSettingsService(
    private val repository: OrganizationSettingsRepository,
    private val clock: Clock,
) {
    @Transactional(readOnly = true)
    fun get(): OrganizationSettings =
        repository.findById(OrganizationSettings.SINGLETON_ID).orElseThrow {
            IllegalStateException("Organisation settings row is missing (migration V7)")
        }

    @Transactional
    fun update(request: UpdateOrganizationRequest): OrganizationSettings {
        val settings = get()
        settings.organizationName = request.organizationName
        settings.senderName = request.senderName
        settings.senderEmail = request.senderEmail
        settings.updatedAt = Instant.now(clock)
        return settings
    }
}
