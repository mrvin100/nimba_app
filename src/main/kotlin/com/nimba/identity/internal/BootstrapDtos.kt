package com.nimba.identity.internal

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Whether the one-time first-admin bootstrap is still available. */
data class BootstrapStatusResponse(
    val available: Boolean,
)

/**
 * Creates the very first platform administrator. Unlike every other account, the
 * bootstrap admin sets its own password directly (there is no one to invite them).
 */
data class BootstrapRequest(
    @field:NotBlank @field:Size(max = 200) val fullName: String,
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8, message = "Le mot de passe doit faire au moins 8 caractères") val password: String,
)
