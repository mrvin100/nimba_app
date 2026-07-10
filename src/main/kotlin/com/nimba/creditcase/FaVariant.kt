package com.nimba.creditcase

/**
 * Which Fiche d'analyse layout applies to a case. Leasing's two contract types have
 * different FA structures from each other and from MC2/MUFFA's.
 */
enum class FaVariant {
    LEASING_AVEC_CONTRAT,
    LEASING_SANS_CONTRAT,
    MC2_MUFFA,
}
