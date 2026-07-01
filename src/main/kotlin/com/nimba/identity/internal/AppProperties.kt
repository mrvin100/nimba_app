package com.nimba.identity.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Application-level settings for the invitation flow. [frontendBaseUrl] is the
 * public origin of the web client, used to build the set-password link; the link
 * is only valid for [invitationTtl].
 */
@ConfigurationProperties("nimba.app")
data class AppProperties(
    val frontendBaseUrl: String = "http://localhost:3000",
    val invitationTtl: Duration = Duration.ofDays(7),
)
