package com.nimba.identity.internal

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
)

data class MembershipDto(
    val department: String,
    val role: String,
)

data class MeResponse(
    val userId: String,
    val fullName: String,
    val email: String,
    val status: String,
    val admin: Boolean,
    val hasAvatar: Boolean,
    val memberships: List<MembershipDto>,
)
