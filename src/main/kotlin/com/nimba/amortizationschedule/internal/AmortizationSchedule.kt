package com.nimba.amortizationschedule.internal

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One uploaded version of a credit case's amortization schedule. The version
 * number increases per case (starting at 1); (case, version) is unique. The three
 * offset parameters are stored here, not as global application config, because the
 * fiche métier specifies they are adjustable per dossier by the analyst.
 *
 * [creditCaseId] references the credit-case module's aggregate by id only — no JPA
 * relationship crosses the module boundary.
 */
@Entity
@Table(name = "amortization_schedule")
class AmortizationSchedule(
    @Column(name = "credit_case_id", nullable = false, updatable = false)
    val creditCaseId: UUID,
    @Column(name = "version_number", nullable = false, updatable = false)
    val versionNumber: Int,
    @Column(name = "original_filename", nullable = false, updatable = false)
    val originalFilename: String,
    @Column(name = "uploaded_by", nullable = false, updatable = false)
    val uploadedBy: UUID,
    @Column(name = "ordinary_offset_months", nullable = false)
    val ordinaryOffsetMonths: Int = 1,
    @Column(name = "vr_offset_months", nullable = false)
    val vrOffsetMonths: Int = 2,
    @Column(name = "fixed_day_of_month", nullable = false)
    val fixedDayOfMonth: Int = 5,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    val uploadedAt: Instant = Instant.now()

    @OneToMany(mappedBy = "schedule", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    val lines: MutableList<AmortizationScheduleLine> = mutableListOf()

    fun addLine(line: AmortizationScheduleLine) {
        line.schedule = this
        lines.add(line)
    }
}
