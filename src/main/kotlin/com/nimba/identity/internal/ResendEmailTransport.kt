package com.nimba.identity.internal

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Resend HTTPS API transport, used when the host blocks outbound SMTP (Render,
 * Fly, Vercel). Activated by `nimba.mail.transport=resend`; requires
 * `nimba.mail.resend.api-key` (`RESEND_API_KEY`) and a sender address on a domain
 * verified in the Resend dashboard (the organisation's `senderEmail`).
 */
@Component
@ConditionalOnProperty(name = ["nimba.mail.transport"], havingValue = "resend")
class ResendEmailTransport(
    @Value("\${nimba.mail.resend.api-key:}") apiKey: String,
    @Value("\${nimba.mail.resend.base-url:https://api.resend.com}") baseUrl: String,
) : EmailTransport {
    private val client: RestClient

    init {
        check(apiKey.isNotBlank()) {
            "nimba.mail.transport=resend but nimba.mail.resend.api-key (RESEND_API_KEY) is not set"
        }
        val factory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(5))
                setReadTimeout(Duration.ofSeconds(15))
            }
        client =
            RestClient
                .builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build()
    }

    override fun send(
        from: String,
        to: String,
        subject: String,
        body: String,
    ) {
        val payload =
            mapOf(
                "from" to from,
                "to" to listOf(to),
                "subject" to subject,
                "text" to body,
            )
        client
            .post()
            .uri("/emails")
            .body(payload)
            .retrieve()
            .toBodilessEntity()
    }
}
