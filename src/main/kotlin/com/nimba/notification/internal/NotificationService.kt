package com.nimba.notification.internal

import com.nimba.identity.Department
import com.nimba.identity.IdentityModuleApi
import com.nimba.notification.NotificationInfo
import com.nimba.notification.NotificationModuleApi
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Writes notifications (the [NotificationModuleApi] the workflow module calls) and
 * serves the bell's read side (this module's own web layer).
 */
@Service
class NotificationService(
    private val notifications: NotificationRepository,
    private val identity: IdentityModuleApi,
) : NotificationModuleApi {
    @Transactional
    override fun notifyUser(
        recipientId: UUID,
        creditCaseId: UUID?,
        message: String,
    ) {
        notifications.save(Notification(recipientId, creditCaseId, message))
    }

    @Transactional
    override fun notifyDepartment(
        department: Department,
        creditCaseId: UUID?,
        message: String,
    ) {
        identity.membersOf(department).forEach { notifyUser(it.id, creditCaseId, message) }
    }

    @Transactional(readOnly = true)
    fun list(
        recipientId: UUID,
        pageable: Pageable,
    ): Page<NotificationInfo> = notifications.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable).map { it.toInfo() }

    @Transactional(readOnly = true)
    fun unreadCount(recipientId: UUID): Long = notifications.countByRecipientIdAndReadIsFalse(recipientId)

    @Transactional
    fun markRead(
        id: UUID,
        recipientId: UUID,
    ): NotificationInfo {
        val notification =
            notifications.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable")
            }
        if (notification.recipientId != recipientId) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cette notification ne vous appartient pas")
        }
        notification.read = true
        return notification.toInfo()
    }

    @Transactional
    fun markAllRead(recipientId: UUID) {
        notifications.findByRecipientIdAndReadIsFalse(recipientId).forEach { it.read = true }
    }
}

internal fun Notification.toInfo(): NotificationInfo =
    NotificationInfo(
        id = requireNotNull(id),
        recipientId = recipientId,
        creditCaseId = creditCaseId,
        message = message,
        read = read,
        createdAt = createdAt,
    )
