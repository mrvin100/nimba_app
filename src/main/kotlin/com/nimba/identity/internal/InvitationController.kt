package com.nimba.identity.internal

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public invitation endpoints (NIMBA-35): validate a set-password token and set the
 * password. These are unauthenticated by design (the user has no credentials yet)
 * and permitted in the security config.
 */
@RestController
@RequestMapping("/auth")
class InvitationController(
    private val invitations: InvitationService,
) {
    @GetMapping("/invitations/{token}")
    fun describe(
        @PathVariable token: String,
    ): InvitationInfoResponse = invitations.describe(token)

    @PostMapping("/set-password")
    fun setPassword(
        @Valid @RequestBody request: SetPasswordRequest,
    ) = invitations.setPassword(request)
}
