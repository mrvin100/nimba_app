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

    /** A client's dossiers in a given status — the matricule-realignment listener only touches BROUILLON ones. */
    fun findByClientIdAndStatus(
        clientId: UUID,
        status: DossierStatus,
    ): List<CautionDossier>
}
