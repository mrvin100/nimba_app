package com.nimba.identity

/**
 * Account lifecycle, controlled by an administrator. Only ACTIVE accounts can
 * authenticate: SUSPENDED is a temporary lock, REVOKED a permanent disable.
 */
enum class AccountStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED,
}
