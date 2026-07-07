package com.nimba.identity.internal

/**
 * Pluggable delivery channel for outbound e-mail. Two implementations exist:
 * [SmtpEmailTransport] for local development (the Mailpit container) and any
 * environment with a reachable SMTP relay, and [ResendEmailTransport] for hosts
 * that block outbound SMTP (Render, Fly, Vercel) — those use Resend's HTTPS API
 * instead. The active transport is selected by `nimba.mail.transport`
 * (`smtp` by default, `resend` in such environments).
 */
interface EmailTransport {
    fun send(
        from: String,
        to: String,
        subject: String,
        body: String,
    )
}
