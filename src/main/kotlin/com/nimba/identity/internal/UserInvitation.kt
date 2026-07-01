package com.nimba.identity.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A one-time invitation that lets a newly provisioned user set their initial
 * password. The [token] is delivered by e-mail as a set-password link; it is valid
 * until [expiresAt] and single-use (marked [consumedAt] once the password is set).
 */
@Entity
@Table(name = "user_invitation")
class UserInvitation(
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "token", nullable = false, unique = true)
    val token: String,
    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null

    fun isUsable(now: Instant): Boolean = consumedAt == null && expiresAt.isAfter(now)
}
