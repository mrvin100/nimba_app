package com.nimba.analysissheet

import com.nimba.creditcase.FaVariant

/**
 * Which sections apply to a given FA variant, in display order. The single
 * source of truth the sections endpoint and its edit gate both read from — a
 * section key not returned here can never be fetched or written for that case.
 */
object FaSectionRegistry {
    // Both leasing variants share this proof set verbatim (their only
    // difference, Pilier 2, is not part of it yet — see FaPilier's KDoc).
    private val LEASING_SECTIONS = FaSectionKey.entries.toList()

    fun sectionsFor(variant: FaVariant): List<FaSectionKey> =
        when (variant) {
            FaVariant.LEASING_AVEC_CONTRAT, FaVariant.LEASING_SANS_CONTRAT -> LEASING_SECTIONS
            // MC2/MUFFA's FA structure has not been provided yet (open point in
            // the design doc) — no sections apply until it is.
            FaVariant.MC2_MUFFA -> emptyList()
        }
}
