package com.nimba.identity.internal

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component

/**
 * Default transport: hands the message to the framework [JavaMailSender], which
 * targets the local Mailpit container in development (`MAIL_HOST`/`MAIL_PORT`) and
 * the configured relay elsewhere. Active unless `nimba.mail.transport=resend`.
 */
@Component
@ConditionalOnProperty(name = ["nimba.mail.transport"], havingValue = "smtp", matchIfMissing = true)
class SmtpEmailTransport(
    private val mailSender: JavaMailSender,
) : EmailTransport {
    override fun send(
        from: String,
        to: String,
        subject: String,
        body: String,
    ) {
        val message = mailSender.createMimeMessage()
        val helper = MimeMessageHelper(message, false, "UTF-8")
        helper.setFrom(from)
        helper.setTo(to)
        helper.setSubject(subject)
        helper.setText(body, false)
        mailSender.send(message)
    }
}
