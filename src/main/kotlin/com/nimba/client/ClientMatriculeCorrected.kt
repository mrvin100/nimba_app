package com.nimba.client

import java.util.UUID

/**
 * Published when a manager corrects a client's matricule (fixing a data-entry
 * mistake, not recording a real identity change). Modules that baked the old
 * matricule into a generated reference string at creation time listen and realign
 * whatever is still safe to touch, without the client module ever knowing who
 * depends on it — a dependency in that direction would be a cycle.
 */
data class ClientMatriculeCorrected(
    val clientId: UUID,
    val oldMatricule: String,
    val newMatricule: String,
)
