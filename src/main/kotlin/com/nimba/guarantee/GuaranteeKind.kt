package com.nimba.guarantee

/**
 * Whether the bank already holds this guarantee or still needs to obtain it. A
 * later epic (client signature) adds a RECUEILLIE transition for the latter; not
 * needed until that workflow exists.
 */
enum class GuaranteeKind {
    DETENUE,
    A_RECUEILLIR,
}
