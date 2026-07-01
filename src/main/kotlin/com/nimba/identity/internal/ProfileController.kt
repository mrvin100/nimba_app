package com.nimba.identity.internal

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

/** Self-service profile update (currently the display name). */
data class UpdateProfileRequest(
    @field:NotBlank @field:Size(max = 200) val fullName: String,
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
        val user =
            users.findById(currentUser.id()).orElseThrow {
                ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentification requise")
            }
        user.fullName = request.fullName
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
