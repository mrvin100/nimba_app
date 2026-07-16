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
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * One editable section's saved content for a case's FA (only [FaSectionKey]s
 * whose [com.nimba.analysissheet.FaSectionType.isEditable] is true ever get a
 * row here — COMPUTED and BOUND sections are never persisted). [analysisSheetId]
 * references the owning [AnalysisSheet] by id only, matching the module's own
 * convention for referencing the credit-case aggregate. [contentJson] is opaque
 * text the frontend defines the exact shape of (plain text for NARRATIVE, a
 * JSON array for TABLE) — this module never parses it, exactly like the sheet's
 * previous single free-text content field.
 */
@Entity
@Table(
    name = "analysis_sheet_section",
    uniqueConstraints = [UniqueConstraint(columnNames = ["analysis_sheet_id", "section_key"])],
)
class AnalysisSheetSection(
    @Column(name = "analysis_sheet_id", nullable = false, updatable = false)
    val analysisSheetId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "section_key", nullable = false, updatable = false)
    val sectionKey: FaSectionKey,
    @Column(name = "content_json", columnDefinition = "TEXT")
    var contentJson: String? = null,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
