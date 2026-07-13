package com.nimba.pv.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PvDebatRowRepository : JpaRepository<PvDebatRow, UUID> {
    fun findByPvIdOrderByOrdreAsc(pvId: UUID): List<PvDebatRow>

    fun deleteByPvId(pvId: UUID)
}
