package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import com.nimba.identity.Department
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class DepartmentCount(
    val department: Department,
    val count: Long,
)

data class UserStatsResponse(
    val total: Long,
    val active: Long,
    val pending: Long,
    val suspended: Long,
    val revoked: Long,
    val byDepartment: List<DepartmentCount>,
)

/**
 * Aggregate user counts for the admin dashboard (NIMBA-32). Under the admin path tree,
 * so it requires ROLE_ADMIN (security config). `active` counts only accounts that have
 * completed their invitation; `pending` counts invited accounts without a password yet.
 */
@RestController
@RequestMapping("/admin/stats/users")
class UserStatsController(
    private val users: UserRepository,
) {
    @GetMapping
    fun get(): UserStatsResponse =
        UserStatsResponse(
            total = users.count(),
            active = users.countByStatusAndPasswordHashIsNotNull(AccountStatus.ACTIVE),
            pending = users.countByPasswordHashIsNull(),
            suspended = users.countByStatus(AccountStatus.SUSPENDED),
            revoked = users.countByStatus(AccountStatus.REVOKED),
            byDepartment = Department.entries.map { DepartmentCount(it, users.countByMembershipDepartment(it)) },
        )
}
