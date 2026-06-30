package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin user-management API (NIMBA-32). The whole admin path tree requires
 * ROLE_ADMIN (security config). Lifecycle transitions are explicit POST actions so
 * the intent (suspend / reactivate / revoke) is unambiguous and auditable.
 */
@RestController
@RequestMapping("/admin/users")
class AdminUserController(
    private val adminUsers: AdminUserService,
) {
    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): AdminUserPage {
        val page = adminUsers.list(pageable)
        return AdminUserPage(
            content = page.content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
            hasPrevious = page.hasPrevious(),
        )
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateUserRequest,
    ): AdminUserResponse = adminUsers.create(request)

    @PostMapping("/{id}/suspend")
    fun suspend(
        @PathVariable id: UUID,
    ): AdminUserResponse = adminUsers.changeStatus(id, AccountStatus.SUSPENDED)

    @PostMapping("/{id}/reactivate")
    fun reactivate(
        @PathVariable id: UUID,
    ): AdminUserResponse = adminUsers.changeStatus(id, AccountStatus.ACTIVE)

    @PostMapping("/{id}/revoke")
    fun revoke(
        @PathVariable id: UUID,
    ): AdminUserResponse = adminUsers.changeStatus(id, AccountStatus.REVOKED)

    @PutMapping("/{id}/memberships")
    fun updateMemberships(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateMembershipsRequest,
    ): AdminUserResponse = adminUsers.updateMemberships(id, request)
}

/** Pagination envelope for the admin user list. */
data class AdminUserPage(
    val content: List<AdminUserResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)
