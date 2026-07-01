package com.nimba.identity.internal

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Minimal invitation info for the set-password page (validates the token). */
data class InvitationInfoResponse(
    val fullName: String,
    val email: String,
)

/** Sets the account password from a valid invitation token. */
data class SetPasswordRequest(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 8, message = "Le mot de passe doit faire au moins 8 caractères") val password: String,
)
