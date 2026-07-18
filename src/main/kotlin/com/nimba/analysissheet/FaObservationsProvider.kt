package com.nimba.analysissheet

import java.util.UUID

/**
 * One observation the comité recorded when returning the dossier for
 * completion (the A_COMPLETER loop) — printed in the FA export's
 * « observations sur le dossier lors du dernier comité de crédit » table,
 * never re-typed by hand. [resolved] is true once the DRI resubmitted the
 * dossier after the observation (printed « Ok » versus « Encours »).
 */
data class FaObservation(
    val observation: String,
    val resolved: Boolean,
)

/**
 * Supplies the comité's completion observations for a dossier. Declared here
 * and implemented by the workflow module — the workflow already depends on
 * this module (publish gate, reopen on return), so the FA export reads the
 * loop through this inverted interface instead of creating a module cycle.
 */
interface FaObservationsProvider {
    fun observationsFor(creditCaseId: UUID): List<FaObservation>
}
