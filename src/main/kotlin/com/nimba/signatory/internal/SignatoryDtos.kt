package com.nimba.signatory.internal

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import com.nimba.signatory.SignatoryCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** One candidate in a signatory picker: either a profile (live name/titre) or a standalone record. */
data class SignatoryOptionResponse(
    val source: SignatorySource,
    val refId: String,
    val nom: String,
    val titre: String,
    val category: SignatoryCategory?,
)

enum class SignatorySource {
    PROFILE,
    STANDALONE,
}

data class SignatoryAuthorizationDto(
    val department: Department,
    val departmentRole: DepartmentRole,
)

data class SignatoryResponse(
    val id: UUID,
    val nom: String,
    val titre: String,
    val category: SignatoryCategory,
    val creationReason: String,
    val createdAt: Instant,
    val authorizations: List<SignatoryAuthorizationDto>,
)

data class SignatoryWriteRequest(
    @field:NotBlank @field:Size(max = 200) val nom: String,
    @field:NotBlank @field:Size(max = 200) val titre: String,
    val category: SignatoryCategory,
    @field:NotBlank @field:Size(max = 500) val creationReason: String,
    @field:Valid val authorizations: List<SignatoryAuthorizationDto> = emptyList(),
)

internal fun Signatory.toResponse() =
    SignatoryResponse(
        id = requireNotNull(id),
        nom = nom,
        titre = titre,
        category = category,
        creationReason = creationReason,
        createdAt = createdAt,
        authorizations = authorizations.map { SignatoryAuthorizationDto(it.department, it.departmentRole) },
    )
