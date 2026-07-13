package com.nimba.fmp

import java.util.UUID

/**
 * The FMP module's public API. Other modules — and this module's own web
 * layer — read and generate a case's FMP through this interface only, never
 * through the repository or entity.
 */
interface FmpModuleApi {
    fun findByCase(creditCaseId: UUID): FmpInfo?

    /**
     * Generates the FMP as a pure extract of the case's finalized PV. 409 if
     * one already exists for the case, 409 if the PV is not FINAL yet.
     */
    fun create(command: CreateFmpCommand): FmpInfo
}
