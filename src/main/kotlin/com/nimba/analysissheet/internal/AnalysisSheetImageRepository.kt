package com.nimba.analysissheet.internal

import com.nimba.analysissheet.FaSectionKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AnalysisSheetImageRepository : JpaRepository<AnalysisSheetImage, UUID> {
    fun findByAnalysisSheetIdOrderByUploadedAt(analysisSheetId: UUID): List<AnalysisSheetImage>

    fun findByAnalysisSheetIdAndSectionKeyOrderByUploadedAt(
        analysisSheetId: UUID,
        sectionKey: FaSectionKey,
    ): List<AnalysisSheetImage>

    fun deleteByAnalysisSheetId(analysisSheetId: UUID)
}
