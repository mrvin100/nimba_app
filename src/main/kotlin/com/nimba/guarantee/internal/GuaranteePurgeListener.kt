package com.nimba.guarantee.internal

import com.nimba.creditcase.CreditCaseDeleted
import com.nimba.creditcase.CreditCaseDocumentResetRequested
import com.nimba.creditcase.ResettableDocument
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Purges a case's guarantees (and their files) when an administrator deletes the
 * case — see the amortization-schedule module's own listener for why the DB rows
 * are removed synchronously and the files only after the commit. Unlike that
 * listener, the files here are individually-keyed MinIO objects, not a directory
 * removable by case id alone, so their storage keys must be read BEFORE the rows
 * are deleted; [pendingKeys] hands them from the pre-commit handler to the
 * post-commit one (both run on the same request, in this one singleton bean).
 */
@Component
class GuaranteePurgeListener(
    private val guarantees: GuaranteeRepository,
    private val attachments: GuaranteeAttachmentService,
) {
    private val pendingKeys = ConcurrentHashMap<UUID, List<String>>()

    @EventListener
    fun purgeRows(event: CreditCaseDeleted) {
        val keys =
            guarantees
                .findByCreditCaseIdOrderByCreatedAtAsc(event.creditCaseId)
                .flatMap { it.attachments }
                .map { it.storageKey }
        pendingKeys[event.creditCaseId] = keys
        guarantees.deleteByCreditCaseId(event.creditCaseId)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun purgeFiles(event: CreditCaseDeleted) {
        val keys = pendingKeys.remove(event.creditCaseId) ?: return
        attachments.deleteFiles(keys)
    }

    /** The Settings tab's « réinitialiser les garanties » — same wipe, dossier kept (design §12.3). */
    @EventListener
    fun reset(event: CreditCaseDocumentResetRequested) {
        if (event.document != ResettableDocument.GARANTIES) return
        purgeRows(CreditCaseDeleted(event.creditCaseId))
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun resetFiles(event: CreditCaseDocumentResetRequested) {
        if (event.document != ResettableDocument.GARANTIES) return
        purgeFiles(CreditCaseDeleted(event.creditCaseId))
    }
}
