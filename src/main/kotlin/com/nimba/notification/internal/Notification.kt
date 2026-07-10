package com.nimba.notification.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One in-app notification for one recipient. [creditCaseId] references the
 * credit-case module's aggregate by id only — no JPA relationship crosses the
 * module boundary; it is nullable because a future notification kind may not be
 * dossier-scoped.
 */
@Entity
@Table(name = "notification")
class Notification(
    @Column(name = "recipient_id", nullable = false, updatable = false)
    val recipientId: UUID,
    @Column(name = "credit_case_id", updatable = false)
    val creditCaseId: UUID?,
    @Column(name = "message", nullable = false, updatable = false)
    val message: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "read", nullable = false)
    var read: Boolean = false

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
