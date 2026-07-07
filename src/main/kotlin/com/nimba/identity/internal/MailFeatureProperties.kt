package com.nimba.identity.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Outbound e-mail settings. [enabled] is the feature switch: when false, invitations
 * are still persisted (so the set-password flow works) but no message is sent —
 * convenient for tests and environments without an SMTP relay. [from]/[fromName] are
 * the fallback sender used when the organisation has not configured its own sender
 * (see [OrganizationSettings.UNCONFIGURED_SENDER_EMAIL]); in production point [from]
 * at an address on a domain verified with the mail provider.
 */
@ConfigurationProperties("nimba.mail")
data class MailFeatureProperties(
    val enabled: Boolean = true,
    val from: String = OrganizationSettings.UNCONFIGURED_SENDER_EMAIL,
    val fromName: String = "Nimba",
)
