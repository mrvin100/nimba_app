package com.nimba.caution.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CautionDocumentVersionRepository : JpaRepository<CautionDocumentVersion, UUID> {
    /** A document's edit history, newest first. */
    fun findByDocumentIdOrderByCreatedAtDesc(documentId: UUID): List<CautionDocumentVersion>

    fun deleteByDocumentIdIn(documentIds: Collection<UUID>)
}
