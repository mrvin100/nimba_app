package com.nimba.notification.internal

import com.nimba.notification.NotificationInfo
import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val creditCaseId: UUID?,
    val message: String,
    val read: Boolean,
    val createdAt: Instant,
)

data class UnreadCountResponse(
    val count: Long,
)

internal fun NotificationInfo.toResponse(): NotificationResponse =
    NotificationResponse(
        id = id,
        creditCaseId = creditCaseId,
        message = message,
        read = read,
        createdAt = createdAt,
    )
