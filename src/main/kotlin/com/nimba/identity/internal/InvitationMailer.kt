package com.nimba.identity.internal

import jakarta.mail.internet.InternetAddress
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Sends the invitation e-mail carrying the set-password link. The sender identity
 * comes from the organisation settings. Delivery goes through the active
 * [EmailTransport] (SMTP locally, Resend on hosts that block outbound SMTP).
 * Sending is best-effort: a failure is logged and swallowed so it never rolls back
 * the account/invitation creation (the admin can always resend). Disabled entirely
 * by `nimba.mail.enabled=false`.
 */
@Component
class InvitationMailer(
    private val emailTransport: EmailTransport,
    private val organization: OrganizationSettingsService,
    private val appProperties: AppProperties,
    private val mailFeature: MailFeatureProperties,
) {
    private val log = LoggerFactory.getLogger(InvitationMailer::class.java)

    fun sendInvitation(
        user: User,
        token: String,
    ) {
        if (!mailFeature.enabled) {
            log.info("Mail disabled: skipping invitation e-mail to {}", user.email)
            return
        }
        val settings = organization.get()
        val link = "${appProperties.frontendBaseUrl.trimEnd('/')}/set-password?token=$token"
        try {
            // Use the organisation's own sender once an admin has set one; until then
            // fall back to the environment default (MAIL_FROM), which must be on a
            // domain verified with the mail provider so the message is not rejected.
            val configured = settings.senderEmail != OrganizationSettings.UNCONFIGURED_SENDER_EMAIL
            val senderEmail = if (configured) settings.senderEmail else mailFeature.from
            val senderName = if (configured) settings.senderName else mailFeature.fromName
            // Encode the sender's display name (accents survive both transports).
            val from = InternetAddress(senderEmail, senderName, "UTF-8").toString()
            val body =
                """
                Bonjour ${user.fullName},

                Un compte vient d'être créé pour vous sur la plateforme ${settings.organizationName}.
                Pour définir votre mot de passe et activer votre accès, ouvrez le lien ci-dessous :

                $link

                Ce lien est valable ${appProperties.invitationTtl.toDays()} jours.

                — $senderName
                """.trimIndent()
            emailTransport.send(from, user.email, "Votre accès à ${settings.organizationName}", body)
            log.info("Invitation e-mail sent to {}", user.email)
        } catch (ex: Exception) {
            log.warn("Failed to send invitation e-mail to {}: {}", user.email, ex.message)
        }
    }
}
