package com.nimba.identity.internal

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Team provisioning for direction managers (NIMBA-35). Authenticated (not admin);
 * the service authorises that the caller manages the target direction. Distinct
 * from the admin API, which manages the whole platform.
 */
@RestController
@RequestMapping("/team/members")
class TeamController(
    private val team: TeamService,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun inviteMember(
        @Valid @RequestBody request: InviteMemberRequest,
    ): AdminUserResponse = team.inviteMember(request)
}
