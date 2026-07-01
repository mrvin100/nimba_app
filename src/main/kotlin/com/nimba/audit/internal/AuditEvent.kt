package com.nimba.audit.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One recorded action in the platform's audit trail: who (actor id/email) did what
 * (a human action label plus the raw method/path), when, and with what outcome
 * (HTTP status), correlated to the request via its correlation id. Written for every
 * state-changing request so a bank operator can reconstruct "who did what, when".
 */
@Entity
@Table(name = "audit_event")
class AuditEvent(
    @Column(name = "occurred_at", nullable = false, updatable = false)
    val occurredAt: Instant,
    @Column(name = "actor_id", updatable = false)
    val actorId: UUID?,
    @Column(name = "actor_email", updatable = false)
    val actorEmail: String?,
    @Column(name = "action", nullable = false, updatable = false)
    val action: String,
    @Column(name = "method", nullable = false, updatable = false)
    val method: String,
    @Column(name = "path", nullable = false, updatable = false)
    val path: String,
    @Column(name = "status", nullable = false, updatable = false)
    val status: Int,
    @Column(name = "correlation_id", updatable = false)
    val correlationId: String?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
}
