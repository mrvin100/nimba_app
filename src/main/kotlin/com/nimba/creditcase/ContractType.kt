package com.nimba.creditcase

/**
 * Only meaningful for [ProductType.LEASING] cases: whether the client has a signed
 * leasing contract already (AVEC_CONTRAT) or not yet (SANS_CONTRAT). The Fiche
 * d'analyse has a different structure for each. Non-leasing products carry no
 * contract type.
 */
enum class ContractType {
    AVEC_CONTRAT,
    SANS_CONTRAT,
}
