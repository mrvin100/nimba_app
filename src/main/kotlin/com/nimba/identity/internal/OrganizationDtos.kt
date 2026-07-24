package com.nimba.identity.internal

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

data class OrganizationResponse(
    val organizationName: String,
    val senderName: String,
    val senderEmail: String,
    val hasLogo: Boolean,
    val signataire1Nom: String?,
    val signataire1Titre: String?,
    val signataire2Nom: String?,
    val signataire2Titre: String?,
    val updatedAt: Instant,
)

data class UpdateOrganizationRequest(
    @field:NotBlank @field:Size(max = 200) val organizationName: String,
    @field:NotBlank @field:Size(max = 200) val senderName: String,
    @field:Email @field:NotBlank @field:Size(max = 320) val senderEmail: String,
    @field:Size(max = 200) val signataire1Nom: String? = null,
    @field:Size(max = 200) val signataire1Titre: String? = null,
    @field:Size(max = 200) val signataire2Nom: String? = null,
    @field:Size(max = 200) val signataire2Titre: String? = null,
)

internal fun OrganizationSettings.toResponse() =
    OrganizationResponse(
        organizationName = organizationName,
        senderName = senderName,
        senderEmail = senderEmail,
        hasLogo = logoKey != null,
        signataire1Nom = signataire1Nom,
        signataire1Titre = signataire1Titre,
        signataire2Nom = signataire2Nom,
        signataire2Titre = signataire2Titre,
        updatedAt = updatedAt,
    )
