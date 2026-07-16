package com.nimba.pv

/**
 * A PV's lifecycle: DCM drafts it (séance date, débats, points forts/faibles,
 * rapporteur/président — freely editable, its BOUND sections read the dossier's
 * live data), then finalizes it once — from then on it is immutable, rendering
 * the [PvSnapshot] frozen at that moment instead of the live dossier.
 */
enum class PvStatus {
    DRAFT,
    FINAL,
}
