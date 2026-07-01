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

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    companion object {
        const val SINGLETON_ID = 1
    }
}
