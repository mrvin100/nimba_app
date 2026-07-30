package com.nimba.caution.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** One immutable snapshot of a document's field answers before and after an edit. */
@Entity
@Table(name = "caution_document_version")
class CautionDocumentVersion(
    @Column(name = "document_id", nullable = false, updatable = false)
    val documentId: UUID,
    @Column(name = "content_before", nullable = false, updatable = false, columnDefinition = "TEXT")
    val contentBefore: String,
    @Column(name = "content_after", nullable = false, updatable = false, columnDefinition = "TEXT")
    val contentAfter: String,
    @Column(name = "reason", updatable = false)
    val reason: String?,
    @Column(name = "actor", nullable = false, updatable = false)
    val actor: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()
}
