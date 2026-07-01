package com.nimba.identity.internal

import com.nimba.identity.Department
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/**
 * A manager invites a member into a direction they manage. The role is always
 * MEMBER — managers provision members, not other managers or admins.
 */
data class InviteMemberRequest(
    @field:NotBlank @field:Size(max = 200) val fullName: String,
    @field:Email @field:NotBlank val email: String,
    @field:NotNull val department: Department,
)
