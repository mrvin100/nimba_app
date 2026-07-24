package com.nimba.client

/**
 * The kind of client. Today the bank's credit products are all corporate, so
 * [ENTREPRISE] is the only value in use; [PARTICULIER] and [AUTRE] are declared so
 * an individual client can be added later as a purely additive change (its
 * particulier-specific fields — nom/prénom/CNI… — join the entity nullable when
 * that product line opens, without a discriminator migration).
 */
enum class ClientType {
    ENTREPRISE,
    PARTICULIER,
    AUTRE,
}
