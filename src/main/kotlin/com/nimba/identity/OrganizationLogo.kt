package com.nimba.identity

/**
 * The organisation logo, safe to share across module boundaries. Other modules obtain
 * it (via [IdentityModuleApi]) to print it on generated documents — never through the
 * internal storage or settings entity.
 */
data class OrganizationLogo(
    val bytes: ByteArray,
    val contentType: String,
)
