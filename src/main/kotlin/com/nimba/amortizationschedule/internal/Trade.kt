package com.nimba.amortizationschedule.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A trade (lettre de change) generated from one amortization-schedule line. Carries
 * a traceable reference to its source line and the schedule version that produced
 * it, the calculated due date, the amount in figures (the line's loyer TTC) and in
 * French words, and the currency. [active] distinguishes the current generation
 * from superseded ones after a re-upload (NIMBA-24).
 */
@Entity
@Table(name = "trade")
class Trade(
    @Column(name = "credit_case_id", nullable = false, updatable = false)
    val creditCaseId: UUID,
    @Column(name = "schedule_id", nullable = false, updatable = false)
    val scheduleId: UUID,
    @Column(name = "source_line_id", nullable = false, updatable = false)
    val sourceLineId: UUID,
    @Column(name = "numero_echeance", nullable = false, updatable = false)
    val numeroEcheance: String,
    @Column(name = "due_date", nullable = false, updatable = false)
    val dueDate: LocalDate,
    @Column(name = "amount", nullable = false, precision = 20, scale = 4, updatable = false)
    val amount: BigDecimal,
    @Column(name = "amount_in_words", nullable = false, updatable = false)
    val amountInWords: String,
    @Column(name = "currency", nullable = false, updatable = false)
    val currency: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "active", nullable = false)
    var active: Boolean = true

    @Column(name = "generated_at", nullable = false, updatable = false)
    val generatedAt: Instant = Instant.now()
}
