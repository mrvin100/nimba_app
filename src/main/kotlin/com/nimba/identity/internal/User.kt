package com.nimba.identity.internal

import com.nimba.identity.AccountStatus
import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A platform user's login identity and authorizations. The email is the unique
 * login id; the password is stored only as a BCrypt hash. Authorizations are a set
 * of [Membership] (direction + role); [platformAdmin] is the orthogonal global
 * administrator capability; [status] governs whether the account may authenticate.
 * Table is `app_user` because `user` is reserved in PostgreSQL.
 */
@Entity
@Table(name = "app_user")
class User(
    @Column(name = "full_name", nullable = false)
    var fullName: String,
    @Column(name = "email", nullable = false, unique = true)
    val email: String,
    // Null until the user sets a password via their invitation (set-password link).
    // A user without a password hash cannot authenticate.
    @Column(name = "password_hash")
    var passwordHash: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AccountStatus = AccountStatus.ACTIVE

    @Column(name = "platform_admin", nullable = false)
    var platformAdmin: Boolean = false

    // Object-storage key of the user's avatar image; null means no avatar.
    @Column(name = "avatar_key")
    var avatarKey: String? = null

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_membership", joinColumns = [JoinColumn(name = "user_id")])
    var memberships: MutableSet<Membership> = mutableSetOf()

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    /** A user who has not yet set a password (invitation not consumed). */
    val pending: Boolean
        get() = passwordHash == null

    /** Sets (or replaces) the role for a direction. */
    fun assign(
        department: Department,
        role: DepartmentRole,
    ) {
        memberships.removeIf { it.department == department }
        memberships.add(Membership(department, role))
    }
}
