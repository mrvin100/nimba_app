package com.nimba.caution.internal

import com.nimba.caution.DossierStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CautionDossierRepository : JpaRepository<CautionDossier, UUID> {
    /** Pages through dossiers, newest first; the client filter is optional. */
    @Query(
        """
        SELECT d FROM CautionDossier d
        WHERE (:clientId IS NULL OR d.clientId = :clientId)
        """,
    )
    fun search(
        clientId: UUID?,
        pageable: Pageable,
    ): Page<CautionDossier>

    /** Count of dossiers in a given lifecycle status, for the admin dashboard. */
    fun countByStatus(status: DossierStatus): Long

    /** The highest sequence already used in the dossier series, or null if none exists yet. */
    @Query("select max(d.sequence) from CautionDossier d")
    fun maxSequence(): Int?
}
