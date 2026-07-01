package com.nimba.audit.internal

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
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
        @PageableDefault(size = 30, sort = ["occurredAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate?,
        @RequestParam(required = false) method: String?,
        @RequestParam(required = false) status: Int?,
    ): AuditPage {
        // Date filters are calendar days (UTC): [from 00:00, to+1 00:00) so `to` is inclusive.
        val fromInstant = from?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
        val toInstant = to?.plusDays(1)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()
        val filter = auditFilter(fromInstant, toInstant, method?.takeIf { it.isNotBlank() }?.uppercase(), status)
        val page = events.findAll(filter, pageable)
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
