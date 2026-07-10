package com.nimba

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder

/** Password shared by every seeded test account. */
const val TEST_PASSWORD = "Pass-Word"

/**
 * Seeds (idempotently) an active DRI analyst — the profile business-endpoint tests
 * authenticate as, since the credit-case surface requires ROLE_DRI_MEMBER. One
 * definition so every endpoint test provisions its analyst identically.
 */
fun seedDriAnalyst(
    users: UserRepository,
    passwordEncoder: PasswordEncoder,
    email: String,
    fullName: String = "Analyste $email",
): User =
    users.findByEmail(email) ?: users.saveAndFlush(
        User(
            fullName = fullName,
            email = email,
            passwordHash = requireNotNull(passwordEncoder.encode(TEST_PASSWORD)),
        ).apply { assign(Department.DRI, DepartmentRole.MEMBER) },
    )

/**
 * Seeds (idempotently) an active member of an arbitrary direction — used by the
 * workflow tests to provision DCM / DRC / COMITE reviewers alongside the DRI.
 */
fun seedMember(
    users: UserRepository,
    passwordEncoder: PasswordEncoder,
    email: String,
    department: Department,
    role: DepartmentRole = DepartmentRole.MEMBER,
    fullName: String = "Membre $email",
): User =
    users.findByEmail(email) ?: users.saveAndFlush(
        User(
            fullName = fullName,
            email = email,
            passwordHash = requireNotNull(passwordEncoder.encode(TEST_PASSWORD)),
        ).apply { assign(department, role) },
    )

/**
 * Seeds (idempotently) an active platform administrator — the profile the
 * administrative dossier actions (archive / restore / delete) require. No
 * direction membership: an admin manages the platform, not a direction's
 * business, and the tests must prove ROLE_ADMIN alone opens those endpoints.
 */
fun seedPlatformAdmin(
    users: UserRepository,
    passwordEncoder: PasswordEncoder,
    email: String,
    fullName: String = "Admin $email",
): User =
    users.findByEmail(email) ?: users.saveAndFlush(
        User(
            fullName = fullName,
            email = email,
            passwordHash = requireNotNull(passwordEncoder.encode(TEST_PASSWORD)),
        ).apply { platformAdmin = true },
    )
