package com.nimba.pv.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PvGuaranteeSnapshotRowRepository : JpaRepository<PvGuaranteeSnapshotRow, UUID> {
    fun findByPvId(pvId: UUID): List<PvGuaranteeSnapshotRow>

    fun deleteByPvId(pvId: UUID)
}
