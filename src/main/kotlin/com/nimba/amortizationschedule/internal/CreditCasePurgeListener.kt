package com.nimba.amortizationschedule.internal

import com.nimba.creditcase.CreditCaseDeleted
import com.nimba.creditcase.CreditCaseDocumentResetRequested
import com.nimba.creditcase.ResettableDocument
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Purges everything this module attaches to a credit case when an administrator
 * deletes it. The event is how the deletion crosses the module boundary without
 * the creditcase module depending on this one (a cycle).
 *
 * The database side (every schedule version — lines cascade — and every trade
 * generation, superseded ones included) runs synchronously inside the publishing
 * transaction, so the case and its dependent rows disappear atomically. The
 * retained CSV files are removed only AFTER the commit: a rollback must be able
 * to leave the dossier exactly as it was, and files cannot be rolled back.
 */
@Component
class CreditCasePurgeListener(
    private val schedules: AmortizationScheduleRepository,
    private val trades: TradeRepository,
    private val fileStorage: ScheduleFileStorage,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun purgeRows(event: CreditCaseDeleted) {
        trades.deleteByCreditCaseId(event.creditCaseId)
        schedules.deleteAll(schedules.findByCreditCaseId(event.creditCaseId))
    }

    /** The Settings tab's « réinitialiser le TA » — same wipe, dossier kept (design §12.3). */
    @EventListener
    fun reset(event: CreditCaseDocumentResetRequested) {
        if (event.document != ResettableDocument.AMORTISSEMENT) return
        purgeRows(CreditCaseDeleted(event.creditCaseId))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun resetFiles(event: CreditCaseDocumentResetRequested) {
        if (event.document != ResettableDocument.AMORTISSEMENT) return
        purgeFiles(CreditCaseDeleted(event.creditCaseId))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun purgeFiles(event: CreditCaseDeleted) {
        // Best-effort: a leftover file is harmless and must never fail a deletion
        // that has already been committed.
        try {
            fileStorage.deleteAll(event.creditCaseId)
        } catch (ex: Exception) {
            log.warn("Fichiers du dossier {} non purgés : {}", event.creditCaseId, ex.message)
        }
    }
}
