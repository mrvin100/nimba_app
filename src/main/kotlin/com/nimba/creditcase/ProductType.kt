package com.nimba.creditcase

/**
 * The credit product a case concerns. Only [LEASING] exists in this phase, but
 * this is modelled as an enum (not a constant) in deliberate anticipation of the
 * classic loan product arriving in a later phase — adding a value then requires no
 * structural change.
 */
enum class ProductType {
    LEASING,
}
