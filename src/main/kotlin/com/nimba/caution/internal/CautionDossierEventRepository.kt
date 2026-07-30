package com.nimba.caution.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CautionDossierEventRepository : JpaRepository<CautionDossierEvent, UUID> {
    /** A dossier's lifecycle journal, newest first. */
    fun findByDossierIdOrderByCreatedAtDesc(dossierId: UUID): List<CautionDossierEvent>

    fun deleteByDossierId(dossierId: UUID)
}
