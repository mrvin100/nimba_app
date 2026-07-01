package com.nimba.identity.internal

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class OrganizationResponse(
    val organizationName: String,
    val senderName: String,
    val senderEmail: String,
    val updatedAt: Instant,
)

data class UpdateOrganizationRequest(
    @field:NotBlank @field:Size(max = 200) val organizationName: String,
    @field:NotBlank @field:Size(max = 200) val senderName: String,
    @field:Email @field:NotBlank @field:Size(max = 320) val senderEmail: String,
)

internal fun OrganizationSettings.toResponse() =
    OrganizationResponse(
        organizationName = organizationName,
        senderName = senderName,
        senderEmail = senderEmail,
        updatedAt = updatedAt,
    )
