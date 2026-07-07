package com.nimba.shared.security

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Cross-origin allow-list for the browser frontend. Locally the frontend and API
 * share an origin, but in staging/production they are deployed as separate
 * services (e.g. on Render) with different origins, so the API must advertise the
 * frontend origin(s) via CORS. [allowedOrigins] binds a comma-separated
 * `CORS_ALLOWED_ORIGINS` (e.g. `https://app.example.com,https://admin.example.com`)
 * into a list; the default is the local dev frontend.
 */
@ConfigurationProperties("nimba.cors")
data class CorsProperties(
    val allowedOrigins: List<String> = listOf("http://localhost:3000"),
)
