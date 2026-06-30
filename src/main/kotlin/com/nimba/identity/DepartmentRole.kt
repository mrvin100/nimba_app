package com.nimba.identity

/**
 * A user's role within a direction. A user holds at most one role per direction.
 * MANAGER inherits MEMBER capabilities through the security role hierarchy.
 */
enum class DepartmentRole {
    MANAGER,
    MEMBER,
}
