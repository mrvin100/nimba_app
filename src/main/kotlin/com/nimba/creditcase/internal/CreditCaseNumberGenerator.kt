package com.nimba.creditcase.internal

import com.nimba.creditcase.ProductType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * Generates the human-readable case number `{productCode}-{year}-{NNNN}` (e.g.
 * `LEA-2026-0001`, `MC2-2026-0007`), sequential and unique within a year per
 * product — same mechanism as [com.nimba.caution.internal.CautionNumberGenerator]
 * (an atomic upsert on a dedicated counter row, so concurrent creations cannot
 * collide or skip), adapted to this product's own convention: unlike a caution
 * dossier, a credit case has no client matricule guaranteed at creation (a
 * leasing client may not have one yet), so the number carries the product
 * instead of the client — the case's client is always shown alongside it, never
 * only in the number. The counter resets each year, and is kept separate per
 * product so one product's volume never shifts another's numbering.
 */
@Component
class CreditCaseNumberGenerator(
    private val jdbcClient: JdbcClient,
    private val clock: Clock,
) {
    @Transactional
    fun nextCaseNumber(productType: ProductType): String {
        val year = LocalDate.now(clock).year
        val sequence =
            jdbcClient
                .sql(
                    """
                    INSERT INTO credit_case_counter (year, product_type, last_value) VALUES (:year, :productType, 1)
                    ON CONFLICT (year, product_type) DO UPDATE SET last_value = credit_case_counter.last_value + 1
                    RETURNING last_value
                    """.trimIndent(),
                ).param("year", year)
                .param("productType", productType.name)
                .query(Int::class.java)
                .single()
        return "%s-%d-%04d".format(productType.code, year, sequence)
    }
}
