package com.nimba.pv.internal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One row of a PV's "débats du comité" table. [ordre] preserves the DCM's entry
 * order (a random UUID id carries no business order). Draft edits replace the
 * whole set rather than patching individual rows — simpler than diffing, and
 * there are only ever a handful of débats per séance.
 */
@Entity
@Table(name = "pv_debat")
class PvDebatRow(
    @Column(name = "pv_id", nullable = false, updatable = false)
    val pvId: UUID,
    @Column(name = "preoccupation", nullable = false, columnDefinition = "TEXT")
    val preoccupation: String,
    @Column(name = "reponse", nullable = false, columnDefinition = "TEXT")
    val reponse: String,
    @Column(name = "recommandation", nullable = false, columnDefinition = "TEXT")
    val recommandation: String,
    @Column(name = "ordre", nullable = false)
    val ordre: Int,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null
}
