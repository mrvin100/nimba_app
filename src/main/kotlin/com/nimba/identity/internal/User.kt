package com.nimba.identity.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A DRI analyst's login identity. The email is the login identifier and is unique
 * at the database level. The password is stored only as a BCrypt hash, never in
 * clear text. Table name is `app_user` because `user` is a reserved word in
 * PostgreSQL.
 */
@Entity
@Table(name = "app_user")
class User(
    @Column(name = "full_name", nullable = false)
    var fullName: String,
    @Column(name = "email", nullable = false, unique = true)
    val email: String,
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    val role: UserRole = UserRole.DRI_ANALYST,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
