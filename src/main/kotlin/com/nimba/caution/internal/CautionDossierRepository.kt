package com.nimba.caution.internal

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
}
