package com.nimba.caution

import java.util.UUID

/**
 * Published after a caution dossier and all of its documents have been removed.
 * Same convention as the credit-case module's deletion events: consumers (audit,
 * future cross-module cleanup) react to this rather than reaching into the
 * caution module's internals.
 */
data class CautionDossierDeleted(
    val dossierId: UUID,
)
