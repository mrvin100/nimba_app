package com.nimba.analysissheet

import java.util.UUID

/**
 * Decides whether the DRI may take a published FA back to draft themselves.
 * Declared here and implemented by the workflow module (which already depends
 * on this one — same inversion as [FaObservationsProvider]): unpublishing is
 * only allowed while the dossier has never left BROUILLON, i.e. before the
 * first review. After a submission, only a reviewer's return reopens the FA.
 */
interface FaUnpublishGate {
    fun canUnpublish(creditCaseId: UUID): Boolean
}
