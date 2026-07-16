package com.nimba.creditcase

/**
 * The credit product a case concerns. Each value has its own required documents,
 * amortization-schedule (TA) format and Fiche d'analyse variant — see
 * `CaseTypePolicy`. [LEASING] additionally carries a [ContractType]; [MC2_MUFFA]
 * (core-banking short-term corporate credit) does not.
 */
enum class ProductType {
    LEASING,
    MC2_MUFFA,
}
