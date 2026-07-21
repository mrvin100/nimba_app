package com.nimba.pv.internal

import com.nimba.creditcase.CreditCaseDeleted
import com.nimba.creditcase.CreditCaseDocumentResetRequested
import com.nimba.creditcase.ResettableDocument
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/** Purges a case's PV (débats and garanties snapshot rows included) when an administrator deletes the case. */
@Component
class PvPurgeListener(
    private val pvs: PvRepository,
    private val debatRows: PvDebatRowRepository,
    private val guaranteeSnapshotRows: PvGuaranteeSnapshotRowRepository,
) {
    @EventListener
    fun purge(event: CreditCaseDeleted) {
        pvs.findByCreditCaseId(event.creditCaseId)?.let { pv ->
            val pvId = requireNotNull(pv.id)
            debatRows.deleteByPvId(pvId)
            guaranteeSnapshotRows.deleteByPvId(pvId)
        }
        pvs.deleteByCreditCaseId(event.creditCaseId)
    }

    /** The Settings tab's « réinitialiser le PV » — same wipe, dossier kept (design §12.3). */
    @EventListener
    fun reset(event: CreditCaseDocumentResetRequested) {
        if (event.document != ResettableDocument.PV) return
        purge(CreditCaseDeleted(event.creditCaseId))
    }
}
