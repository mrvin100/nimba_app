package com.nimba.analysissheet.internal

import com.nimba.analysissheet.FaSectionKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnalysisSheetSectionRepository : JpaRepository<AnalysisSheetSection, UUID> {
    fun findByAnalysisSheetId(analysisSheetId: UUID): List<AnalysisSheetSection>

    fun findByAnalysisSheetIdAndSectionKey(
        analysisSheetId: UUID,
        sectionKey: FaSectionKey,
    ): AnalysisSheetSection?

    fun deleteByAnalysisSheetId(analysisSheetId: UUID)
}
