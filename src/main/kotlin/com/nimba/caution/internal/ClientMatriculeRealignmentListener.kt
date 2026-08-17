package com.nimba.caution.internal

import com.nimba.caution.CautionStatus
import com.nimba.client.ClientMatriculeCorrected
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Keeps a caution document's reference number aligned with its client's
 * matricule after a correction. The matricule is baked into the reference
 * string once, at creation — realigning it here only ever touches a DRAFT
 * document's reference, which hasn't been handed to anyone outside the bank
 * yet. Anything already finalized keeps whatever matricule it was issued
 * with, on purpose (see [CautionClientSnapshot], a deliberate point-in-time
 * identity snapshot) — the same reason a finalized document can't be deleted
 * either. A dossier's own reference number does not embed the matricule at
 * all, so it needs no realignment.
 */
@Component
class ClientMatriculeRealignmentListener(
    private val documents: CautionDocumentRepository,
) {
    @EventListener
    @Transactional
    fun realign(event: ClientMatriculeCorrected) {
        documents.findByClientIdAndStatus(event.clientId, CautionStatus.DRAFT).forEach {
            it.referenceNumber = it.referenceNumber.replaceFirst(event.oldMatricule, event.newMatricule)
        }
    }
}
