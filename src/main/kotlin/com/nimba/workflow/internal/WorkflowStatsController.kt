package com.nimba.workflow.internal

import com.nimba.identity.Department
import com.nimba.workflow.WorkflowStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class WorkflowStatusCount(
    val status: WorkflowStatus,
    val count: Long,
)

data class DepartmentPendingCount(
    val department: Department,
    val count: Long,
)

data class WorkflowStatusDuration(
    val status: WorkflowStatus,
    val averageHours: Double,
)

data class WorkflowStatsResponse(
    val byStatus: List<WorkflowStatusCount>,
    val pendingByDepartment: List<DepartmentPendingCount>,
    val averageDurationByStatus: List<WorkflowStatusDuration>,
)

/**
 * Aggregate financement-workflow figures for the admin dashboard: the cross-directorate
 * funnel, how many dossiers currently sit with each direction, and how long a dossier
 * typically stays at each step (from the event journal). Under the admin path tree, so
 * it requires ROLE_ADMIN (security config).
 */
@RestController
@RequestMapping("/admin/stats/workflow")
class WorkflowStatsController(
    private val workflowService: WorkflowService,
) {
    @GetMapping
    fun get(): WorkflowStatsResponse {
        val durations = workflowService.averageHoursInStatus()
        return WorkflowStatsResponse(
            byStatus = workflowService.statusCounts().map { (status, count) -> WorkflowStatusCount(status, count) },
            pendingByDepartment =
                workflowService.pendingCountByDepartment().map { (department, count) -> DepartmentPendingCount(department, count) },
            averageDurationByStatus = durations.map { (status, hours) -> WorkflowStatusDuration(status, hours) },
        )
    }
}
