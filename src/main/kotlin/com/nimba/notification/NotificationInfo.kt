package com.nimba.notification

import java.time.Instant
import java.util.UUID

/** Read-only view of a notification, safe to share across module boundaries. */
data class NotificationInfo(
    val id: UUID,
    val recipientId: UUID,
    val creditCaseId: UUID?,
    val message: String,
    val read: Boolean,
    val createdAt: Instant,
)
