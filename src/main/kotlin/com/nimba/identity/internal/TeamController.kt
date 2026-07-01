package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Team management for direction managers (NIMBA-35/37). Authenticated (not admin);
 * the service authorises that the caller manages the target direction. A manager
 * lists and manages the members of the directions they manage (invite, suspend,
 * reactivate, revoke). Distinct from the admin API, which manages the whole platform.
 */
@RestController
@RequestMapping("/team/members")
class TeamController(
    private val team: TeamService,
) {
    @GetMapping
    fun members(): List<AdminUserResponse> = team.listMembers()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun inviteMember(
        @Valid @RequestBody request: InviteMemberRequest,
    ): AdminUserResponse = team.inviteMember(request)

    @PostMapping("/{id}/suspend")
    fun suspend(
        @PathVariable id: UUID,
    ): AdminUserResponse = team.changeMemberStatus(id, AccountStatus.SUSPENDED)

    @PostMapping("/{id}/reactivate")
    fun reactivate(
        @PathVariable id: UUID,
    ): AdminUserResponse = team.changeMemberStatus(id, AccountStatus.ACTIVE)

    @PostMapping("/{id}/revoke")
    fun revoke(
        @PathVariable id: UUID,
    ): AdminUserResponse = team.changeMemberStatus(id, AccountStatus.REVOKED)
}
