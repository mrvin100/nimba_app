package com.nimba.creditcase

import com.nimba.catalog.ProductFamily

/**
 * The credit product a case concerns. Each value has its own required documents,
 * amortization-schedule (TA) format and Fiche d'analyse variant — see
 * `CaseTypePolicy`. [LEASING] additionally carries a [ContractType]; [MC2_MUFFA]
 * (core-banking short-term corporate credit) does not.
 *
 * These are the *financement* module's own products; the system-wide taxonomy is
 * [ProductFamily] (which also spans the caution product). [family] binds the two so
 * there is a single link between a case's product and the catalogue.
 */
enum class ProductType {
    LEASING,
    MC2_MUFFA,
    ;

    fun family(): ProductFamily =
        when (this) {
            LEASING -> ProductFamily.LEASING
            MC2_MUFFA -> ProductFamily.MC2_MUFFA
        }
}
