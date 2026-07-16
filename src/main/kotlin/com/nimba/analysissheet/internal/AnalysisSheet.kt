package com.nimba.analysissheet.internal

import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.creditcase.FaVariant
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
 * A case's Fiche d'analyse. [creditCaseId] references the credit-case module's
 * aggregate by id only — no JPA relationship crosses the module boundary. Its
 * content lives in per-section [AnalysisSheetSection] rows (see
 * [com.nimba.analysissheet.FaSectionRegistry]), not on this entity.
 */
@Entity
@Table(name = "analysis_sheet")
class AnalysisSheet(
    @Column(name = "credit_case_id", nullable = false, updatable = false)
    val creditCaseId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "fa_variant", nullable = false, updatable = false)
    val faVariant: FaVariant,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: AnalysisSheetStatus = AnalysisSheetStatus.DRAFT

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "published_at")
    var publishedAt: Instant? = null
}
