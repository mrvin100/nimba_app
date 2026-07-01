package com.nimba.audit.internal

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class AuditEventResponse(
    val id: UUID,
    val occurredAt: Instant,
    val actorEmail: String?,
    val action: String,
    val method: String,
    val path: String,
    val status: Int,
    val correlationId: String?,
)

data class AuditPage(
    val content: List<AuditEventResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

/**
 * Read-only audit trail API (NIMBA-40). Under the admin path tree, so it requires
 * ROLE_ADMIN (security config). Newest first.
 */
@RestController
@RequestMapping("/admin/audit")
class AuditController(
    private val events: AuditEventRepository,
) {
    @GetMapping
    fun list(
        @PageableDefault(size = 30) pageable: Pageable,
    ): AuditPage {
        val page = events.findAllByOrderByOccurredAtDesc(pageable)
        return AuditPage(
            content = page.content.map { it.toResponse() },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
            hasPrevious = page.hasPrevious(),
        )
    }

    private fun AuditEvent.toResponse() =
        AuditEventResponse(
            id = requireNotNull(id),
            occurredAt = occurredAt,
            actorEmail = actorEmail,
            action = action,
            method = method,
            path = path,
            status = status,
            correlationId = correlationId,
        )
}
