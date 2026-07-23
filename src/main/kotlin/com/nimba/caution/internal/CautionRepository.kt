package com.nimba.caution.internal

import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CautionRepository : JpaRepository<Caution, UUID> {
    @Query(
        """
        select c from Caution c
        where (:clientId is null or c.clientId = :clientId)
          and (:documentType is null or c.documentType = :documentType)
          and (:status is null or c.status = :status)
        """,
    )
    fun search(
        @Param("clientId") clientId: UUID?,
        @Param("documentType") documentType: CautionDocumentType?,
        @Param("status") status: CautionStatus?,
        pageable: Pageable,
    ): Page<Caution>

    fun findByDossierIdOrderByCreatedAtDesc(dossierId: UUID): List<Caution>

    fun deleteByDossierId(dossierId: UUID)
}
