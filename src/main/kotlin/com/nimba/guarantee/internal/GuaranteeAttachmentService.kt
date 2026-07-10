package com.nimba.guarantee.internal

import com.nimba.guarantee.GuaranteeAttachmentObject
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * The guarantee module's only MinIO-touching service, isolated from
 * [GuaranteeModuleApiService] so that class's CRUD logic stays meaningfully
 * covered by tests — object-storage I/O is verified against a running MinIO, not
 * unit tests, matching the identity module's Avatar/OrganizationLogo services
 * (see the Kover exclude list).
 */
@Service
class GuaranteeAttachmentService(
    private val guarantees: GuaranteeRepository,
    private val storage: GuaranteeAttachmentStorage,
) {
    @Transactional
    fun add(
        guaranteeId: UUID,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        uploadedBy: UUID,
    ): Guarantee {
        val guarantee = requireGuarantee(guaranteeId)
        val attachmentId = UUID.randomUUID()
        val key = storage.upload(guaranteeId, attachmentId, fileName, contentType, bytes)
        guarantee.addAttachment(
            GuaranteeAttachment(
                fileName = fileName,
                contentType = contentType,
                sizeBytes = bytes.size.toLong(),
                storageKey = key,
                uploadedBy = uploadedBy,
            ).apply { id = attachmentId },
        )
        guarantee.updatedAt = Instant.now()
        return guarantee
    }

    @Transactional
    fun remove(
        guaranteeId: UUID,
        attachmentId: UUID,
    ): Guarantee {
        val guarantee = requireGuarantee(guaranteeId)
        val attachment = guarantee.attachments.find { it.id == attachmentId } ?: throw attachmentNotFound()
        guarantee.attachments.remove(attachment)
        guarantee.updatedAt = Instant.now()
        // Best-effort, after the row is detached from its parent in this transaction.
        runCatching { storage.delete(attachment.storageKey) }
        return guarantee
    }

    @Transactional(readOnly = true)
    fun read(
        guaranteeId: UUID,
        attachmentId: UUID,
    ): GuaranteeAttachmentObject {
        val guarantee = requireGuarantee(guaranteeId)
        val attachment = guarantee.attachments.find { it.id == attachmentId } ?: throw attachmentNotFound()
        return GuaranteeAttachmentObject(
            fileName = attachment.fileName,
            contentType = attachment.contentType,
            bytes = storage.load(attachment.storageKey),
        )
    }

    /** Best-effort cleanup of already-orphaned files (a guarantee's own delete, or the case-purge listener). */
    fun deleteFiles(storageKeys: List<String>) {
        storageKeys.forEach { runCatching { storage.delete(it) } }
    }

    private fun requireGuarantee(id: UUID): Guarantee =
        guarantees.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Garantie introuvable") }

    private fun attachmentNotFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Pièce jointe introuvable")
}
