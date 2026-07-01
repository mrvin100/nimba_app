package com.nimba.identity.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Single-row organisation settings (id is always [SINGLETON_ID]). Holds the sender
 * identity used for invitation e-mails and the organisation name shown in the UI.
 * The row is created by migration V7, so it always exists.
 */
@Entity
@Table(name = "organization_settings")
class OrganizationSettings {
    @Id
    @Column(name = "id")
    val id: Int = SINGLETON_ID

    @Column(name = "organization_name", nullable = false)
    var organizationName: String = "Nimba"

    @Column(name = "sender_name", nullable = false)
    var senderName: String = "Nimba"

    @Column(name = "sender_email", nullable = false)
    var senderEmail: String = "no-reply@nimba.local"

    /** Object-storage key of the organisation logo, or null when none is configured. */
    @Column(name = "logo_key")
    var logoKey: String? = null

    /** Content type of the stored logo (e.g. `image/png`), used when serving it. */
    @Column(name = "logo_content_type")
    var logoContentType: String? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    companion object {
        const val SINGLETON_ID = 1
    }
}
