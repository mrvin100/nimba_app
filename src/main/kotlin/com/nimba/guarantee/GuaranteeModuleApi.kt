package com.nimba.guarantee

import java.util.UUID

/**
 * The guarantee module's public API. Other modules read and write a case's
 * guarantees through this interface only — never through the repository or
 * entities, which are internal to the module. The module's own web layer goes
 * through this facade too, so every consumer sees one behavior.
 */
interface GuaranteeModuleApi {
    fun create(command: CreateGuaranteeCommand): GuaranteeInfo

    fun update(
        id: UUID,
        command: UpdateGuaranteeCommand,
    ): GuaranteeInfo

    /** 404 if unknown. */
    fun delete(id: UUID)

    fun findById(id: UUID): GuaranteeInfo?

    /** Every guarantee of a case, oldest first. */
    fun listByCase(creditCaseId: UUID): List<GuaranteeInfo>

    /** Adds a file to a guarantee and returns it with the new attachment included. */
    fun addAttachment(
        guaranteeId: UUID,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        uploadedBy: UUID,
    ): GuaranteeInfo

    /** Removes a file from a guarantee and returns it without the attachment. */
    fun removeAttachment(
        guaranteeId: UUID,
        attachmentId: UUID,
    ): GuaranteeInfo

    /** Loads a file for download; 404 if the guarantee or the attachment is unknown. */
    fun readAttachment(
        guaranteeId: UUID,
        attachmentId: UUID,
    ): GuaranteeAttachmentObject
}
