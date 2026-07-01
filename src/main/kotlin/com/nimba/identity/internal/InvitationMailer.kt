package com.nimba.identity.internal

import org.slf4j.LoggerFactory
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * Sends the invitation e-mail carrying the set-password link. The sender identity
 * comes from the organisation settings. Sending is best-effort: a relay failure is
 * logged and swallowed so it never rolls back the account/invitation creation (the
 * admin can always resend). Disabled entirely by `nimba.mail.enabled=false`.
 */
@Component
class InvitationMailer(
    private val mailSender: JavaMailSender,
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
            val message = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, false, "UTF-8")
            helper.setFrom(settings.senderEmail, settings.senderName)
            helper.setTo(user.email)
            helper.setSubject("Votre accès à ${settings.organizationName}")
            helper.setText(
                """
                Bonjour ${user.fullName},

                Un compte vient d'être créé pour vous sur la plateforme ${settings.organizationName}.
                Pour définir votre mot de passe et activer votre accès, ouvrez le lien ci-dessous :

                $link

                Ce lien est valable ${appProperties.invitationTtl.toDays()} jours.

                — ${settings.senderName}
                """.trimIndent(),
                false,
            )
            mailSender.send(message)
            log.info("Invitation e-mail sent to {}", user.email)
        } catch (ex: Exception) {
            log.warn("Failed to send invitation e-mail to {}: {}", user.email, ex.message)
        }
    }
}
