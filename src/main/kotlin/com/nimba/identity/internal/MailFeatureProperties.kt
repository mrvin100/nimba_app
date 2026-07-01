package com.nimba.identity.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Feature switch for outbound e-mail. When disabled, invitations are still
 * persisted (so the set-password flow works) but no message is sent — convenient
 * for tests and environments without an SMTP relay.
 */
@ConfigurationProperties("nimba.mail")
data class MailFeatureProperties(
    val enabled: Boolean = true,
)
