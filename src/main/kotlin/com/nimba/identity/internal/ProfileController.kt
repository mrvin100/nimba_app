package com.nimba.identity.internal

import com.nimba.identity.Civility
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Self-service profile update: the display name, job title, civility, and whether
 * the user opts in to appear as a pickable signatory (requires a non-blank [titre]
 * and a [civility]).
 */
data class UpdateProfileRequest(
    @field:NotBlank @field:Size(max = 200) val fullName: String,
    @field:Size(max = 200) val titre: String? = null,
    val civility: Civility? = null,
    val signatoryOptIn: Boolean = false,
)

/**
 * Current user's own profile (NIMBA-37). Authenticated; a user edits only their own
 * account, so there is no id in the path — the principal identifies the target.
 */
@Service
class ProfileService(
    private val users: UserRepository,
    private val currentUser: CurrentUser,
) {
    @Transactional
    fun updateName(request: UpdateProfileRequest): MeResponse {
        val titre = request.titre?.takeIf { it.isNotBlank() }
        if (request.signatoryOptIn && (titre == null || request.civility == null)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Un titre et une civilité sont requis pour apparaître comme signataire")
        }
        val user = users.caller(currentUser)
        user.fullName = request.fullName
        user.titre = titre
        user.civility = request.civility
        user.signatoryOptIn = request.signatoryOptIn
        return user.toMeResponse()
    }
}

@RestController
@RequestMapping("/auth/profile")
class ProfileController(
    private val profile: ProfileService,
) {
    @PutMapping
    fun update(
        @Valid @RequestBody request: UpdateProfileRequest,
    ): MeResponse = profile.updateName(request)
}
