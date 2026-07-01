package com.nimba.audit.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Persists one audit entry. Kept tiny so recording never disturbs the audited action. */
@Service
class AuditRecorder(
    private val events: AuditEventRepository,
    private val clock: Clock,
) {
    @Transactional
    fun record(
        actorId: UUID?,
        actorEmail: String?,
        action: String,
        method: String,
        path: String,
        status: Int,
        correlationId: String?,
    ) {
        events.save(
            AuditEvent(
                occurredAt = Instant.now(clock),
                actorId = actorId,
                actorEmail = actorEmail,
                action = action,
                method = method,
                path = path,
                status = status,
                correlationId = correlationId,
            ),
        )
    }
}
