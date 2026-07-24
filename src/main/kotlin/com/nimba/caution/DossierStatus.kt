package com.nimba.caution

/**
 * A caution dossier's lifecycle. It stays OPEN while documents are still being
 * added or amended (late additions, prorogations), and is CLOSED once the DCM
 * considers the request fully served. Individual documents keep their own
 * DRAFT/FINAL status independently ([CautionStatus]).
 */
enum class DossierStatus {
    OPEN,
    CLOSED,
}
