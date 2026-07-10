package com.nimba.guarantee.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** One proof file of a guarantee (e.g. a signed domiciliation letter, a carte grise). */
@Entity
@Table(name = "guarantee_attachment")
class GuaranteeAttachment(
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
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    val uploadedAt: Instant = Instant.now()

    @ManyToOne
    @JoinColumn(name = "guarantee_id", nullable = false)
    var guarantee: Guarantee? = null
}
