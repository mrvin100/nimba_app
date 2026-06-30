package com.nimba.identity

/**
 * A direction of the credit-case process, in process order (DRI first). Extensible:
 * adding a direction here and a workspace for it requires no structural change to
 * the RBAC model. Declaration order is the landing priority for multi-direction
 * users.
 */
enum class Department {
    DRI,
    DCM,
    DRC,
}
