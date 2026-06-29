package com.nimba.identity.internal

/**
 * Analyst roles. Only [DRI_ANALYST] exists in this phase — trade generation needs
 * no hierarchical validation (backlog NIMBA-8, fiche métier 10.1). A multi-role
 * permission system is deliberately not introduced before a second real role
 * (DCM, Risques, Comité) exists; this enum is the single, hardcoded role.
 */
enum class UserRole {
    DRI_ANALYST,
}
