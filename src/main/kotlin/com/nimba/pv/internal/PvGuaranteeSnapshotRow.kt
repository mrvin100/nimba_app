package com.nimba.pv.internal

import com.nimba.guarantee.GuaranteeKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** One guarantee frozen onto a finalized PV — written once at finalization, never updated. */
@Entity
@Table(name = "pv_guarantee_snapshot")
class PvGuaranteeSnapshotRow(
    @Column(name = "pv_id", nullable = false, updatable = false)
    val pvId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    val kind: GuaranteeKind,
    @Column(name = "description", nullable = false, updatable = false, columnDefinition = "TEXT")
    val description: String,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
}
