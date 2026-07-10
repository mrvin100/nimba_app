package com.nimba.notification.internal

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {
    fun findByRecipientIdOrderByCreatedAtDesc(
        recipientId: UUID,
        pageable: Pageable,
    ): Page<Notification>

    fun countByRecipientIdAndReadIsFalse(recipientId: UUID): Long

    fun findByRecipientIdAndReadIsFalse(recipientId: UUID): List<Notification>
}
