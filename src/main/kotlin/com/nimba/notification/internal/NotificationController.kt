package com.nimba.notification.internal

import com.nimba.notification.NotificationInfo
import com.nimba.shared.CurrentUser
import com.nimba.shared.PageResponse
import com.nimba.shared.toPageResponse
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The bell's read/write surface. Open to every authenticated user (no direction
 * restriction — each user only ever sees their own notifications, enforced by the
 * service scoping every query to the caller's id).
 */
@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val notifications: NotificationService,
    private val currentUser: CurrentUser,
) {
    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): PageResponse<NotificationResponse> = notifications.list(currentUser.id(), pageable).toPageResponse(NotificationInfo::toResponse)

    @GetMapping("/unread-count")
    fun unreadCount(): UnreadCountResponse = UnreadCountResponse(notifications.unreadCount(currentUser.id()))

    @PostMapping("/{id}/read")
    fun markRead(
        @PathVariable id: UUID,
    ): NotificationResponse = notifications.markRead(id, currentUser.id()).toResponse()

    @PostMapping("/read-all")
    fun markAllRead() {
        notifications.markAllRead(currentUser.id())
    }
}
