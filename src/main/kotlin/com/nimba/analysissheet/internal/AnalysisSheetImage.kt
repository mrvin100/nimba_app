package com.nimba.analysissheet.internal

import com.nimba.analysissheet.FaSectionKey
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One uploaded figure of an IMAGE-type FA section (organigramme, figure du
 * marché, facture proforma, annexe) — embedded at the section's position in
 * the exported document, in upload order. Scoped to the sheet + section key
 * rather than the lazily-created section row, so an image can arrive before
 * any text was saved.
 */
@Entity
@Table(name = "analysis_sheet_image")
class AnalysisSheetImage(
    @Column(name = "analysis_sheet_id", nullable = false, updatable = false)
    val analysisSheetId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "section_key", nullable = false, updatable = false)
    val sectionKey: FaSectionKey,
    @Column(name = "file_name", nullable = false)
    val fileName: String,
    @Column(name = "content_type", nullable = false)
    val contentType: String,
    @Column(name = "size_bytes", nullable = false)
    val sizeBytes: Long,
    @Column(name = "storage_key", nullable = false)
    val storageKey: String,
    @Column(name = "uploaded_by", nullable = false, updatable = false)
    val uploadedBy: UUID,
    @Column(name = "caption")
    var caption: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    val uploadedAt: Instant = Instant.now()
}
