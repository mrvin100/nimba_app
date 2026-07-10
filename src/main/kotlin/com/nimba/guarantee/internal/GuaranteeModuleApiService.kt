package com.nimba.guarantee.internal

import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.guarantee.CreateGuaranteeCommand
import com.nimba.guarantee.GuaranteeAttachmentInfo
import com.nimba.guarantee.GuaranteeAttachmentObject
import com.nimba.guarantee.GuaranteeInfo
import com.nimba.guarantee.GuaranteeModuleApi
import com.nimba.guarantee.UpdateGuaranteeCommand
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class GuaranteeModuleApiService(
    private val guarantees: GuaranteeRepository,
    private val creditCases: CreditCaseModuleApi,
    private val attachments: GuaranteeAttachmentService,
) : GuaranteeModuleApi {
    @Transactional
    override fun create(command: CreateGuaranteeCommand): GuaranteeInfo {
        creditCases.getOrThrow(command.creditCaseId)
        val saved =
            guarantees.save(
                Guarantee(
                    creditCaseId = command.creditCaseId,
                    kind = command.kind,
                    description = command.description,
                    createdBy = command.createdBy,
                ),
            )
        return saved.toInfo()
    }

    @Transactional
    override fun update(
        id: UUID,
        command: UpdateGuaranteeCommand,
    ): GuaranteeInfo {
        val guarantee = requireGuarantee(id)
        guarantee.kind = command.kind
        guarantee.description = command.description
        guarantee.updatedAt = Instant.now()
        return guarantee.toInfo()
    }

    @Transactional
    override fun delete(id: UUID) {
        val guarantee = requireGuarantee(id)
        val keys = guarantee.attachments.map { it.storageKey }
        guarantees.delete(guarantee)
        attachments.deleteFiles(keys)
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): GuaranteeInfo? = guarantees.findById(id).map { it.toInfo() }.orElse(null)

    @Transactional(readOnly = true)
    override fun listByCase(creditCaseId: UUID): List<GuaranteeInfo> =
        guarantees.findByCreditCaseIdOrderByCreatedAtAsc(creditCaseId).map { it.toInfo() }

    override fun addAttachment(
        guaranteeId: UUID,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        uploadedBy: UUID,
    ): GuaranteeInfo = attachments.add(guaranteeId, fileName, contentType, bytes, uploadedBy).toInfo()

    override fun removeAttachment(
        guaranteeId: UUID,
        attachmentId: UUID,
    ): GuaranteeInfo = attachments.remove(guaranteeId, attachmentId).toInfo()

    override fun readAttachment(
        guaranteeId: UUID,
        attachmentId: UUID,
    ): GuaranteeAttachmentObject = attachments.read(guaranteeId, attachmentId)

    private fun requireGuarantee(id: UUID): Guarantee =
        guarantees.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Garantie introuvable") }
}

internal fun Guarantee.toInfo(): GuaranteeInfo =
    GuaranteeInfo(
        id = requireNotNull(id),
        creditCaseId = creditCaseId,
        kind = kind,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        attachments = attachments.map { it.toInfo() },
    )

private fun GuaranteeAttachment.toInfo(): GuaranteeAttachmentInfo =
    GuaranteeAttachmentInfo(
        id = requireNotNull(id),
        fileName = fileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        uploadedAt = uploadedAt,
    )
