package com.nimba.identity.internal

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

/**
 * A (direction, role) pair a user belongs to. Value object stored in the
 * `user_membership` collection table; the primary key (user_id, department)
 * enforces at most one role per direction per user.
 */
@Embeddable
data class Membership(
    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false)
    val department: Department,
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    val role: DepartmentRole,
)
