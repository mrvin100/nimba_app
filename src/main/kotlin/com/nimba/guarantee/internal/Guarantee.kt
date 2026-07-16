package com.nimba.guarantee.internal

import com.nimba.guarantee.GuaranteeKind
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
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
 * One guarantee of a dossier — held by the bank already, or still to be obtained.
 * [creditCaseId] references the credit-case module's aggregate by id only — no JPA
 * relationship crosses the module boundary. Bound into the FA (§4.2/§5), the PV and
 * the FMP.
 */
@Entity
@Table(name = "guarantee")
class Guarantee(
    @Column(name = "credit_case_id", nullable = false, updatable = false)
    val creditCaseId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    var kind: GuaranteeKind,
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    var description: String,
    @Column(name = "created_by", nullable = false, updatable = false)
    val createdBy: UUID,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @OneToMany(mappedBy = "guarantee", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("uploadedAt ASC")
    val attachments: MutableList<GuaranteeAttachment> = mutableListOf()

    fun addAttachment(attachment: GuaranteeAttachment) {
        attachment.guarantee = this
        attachments.add(attachment)
    }
}
