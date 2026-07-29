package com.nimba.caution.internal

import com.nimba.caution.CautionStatus
import com.nimba.caution.DossierStatus
import com.nimba.client.ClientMatriculeCorrected
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Keeps a caution reference number aligned with its client's matricule after a
 * correction. The matricule is baked into the reference string once, at
 * creation — realigning it here only ever touches records still safe to change:
 * a BROUILLON dossier's reference, or a DRAFT document's, neither of which has
 * been handed to anyone outside the bank yet. Anything already finalized keeps
 * whatever matricule it was issued with, on purpose (see [CautionClientSnapshot],
 * a deliberate point-in-time identity snapshot) — the same reason a finalized
 * dossier cannot be deleted either.
 */
@Component
class ClientMatriculeRealignmentListener(
    private val dossiers: CautionDossierRepository,
    private val documents: CautionDocumentRepository,
) {
    @EventListener
    @Transactional
    fun realign(event: ClientMatriculeCorrected) {
        dossiers.findByClientIdAndStatus(event.clientId, DossierStatus.BROUILLON).forEach {
            it.referenceNumber = it.referenceNumber.replaceFirst(event.oldMatricule, event.newMatricule)
        }
        documents.findByClientIdAndStatus(event.clientId, CautionStatus.DRAFT).forEach {
            it.referenceNumber = it.referenceNumber.replaceFirst(event.oldMatricule, event.newMatricule)
        }
    }
}
