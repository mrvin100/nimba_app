package com.nimba.identity.internal

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

data class MembershipPayload(
    @field:NotNull val department: Department,
    @field:NotNull val role: DepartmentRole,
)

data class CreateUserRequest(
    @field:NotBlank @field:Size(max = 200) val fullName: String,
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8, message = "Le mot de passe doit faire au moins 8 caractères") val password: String,
    val admin: Boolean = false,
    @field:Valid val memberships: List<MembershipPayload> = emptyList(),
)

data class UpdateMembershipsRequest(
    val admin: Boolean = false,
    @field:Valid val memberships: List<MembershipPayload> = emptyList(),
)

data class AdminUserResponse(
    val id: UUID,
    val fullName: String,
    val email: String,
    val status: String,
    val admin: Boolean,
    val memberships: List<MembershipPayload>,
    val createdAt: Instant,
)

internal fun User.toAdminResponse(): AdminUserResponse =
    AdminUserResponse(
        id = requireNotNull(id),
        fullName = fullName,
        email = email,
        status = status.name,
        admin = platformAdmin,
        memberships = memberships.map { MembershipPayload(it.department, it.role) },
        createdAt = createdAt,
    )
