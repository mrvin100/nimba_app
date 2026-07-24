package com.nimba.caution.internal

import com.nimba.caution.CautionDocumentType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Generates a caution's reference number:
 * `{sequence}-{matricule}-{documentType}-{date}` (e.g. `04370-038044-SMS-11-02-26`).
 * The sequence is a single global counter shared by every document type —
 * confirmed against the bank's real references (a SMS and an ACF for the
 * same client sat 19 apart) — produced by an atomic upsert on a dedicated
 * counter row, the same technique as [com.nimba.creditcase.internal.CreditCaseNumberGenerator],
 * so concurrent creations cannot collide or skip.
 */
@Component
class CautionNumberGenerator(
    private val jdbcClient: JdbcClient,
    private val clock: Clock,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd-MM-uu")

    @Transactional
    fun nextReferenceNumber(
        matricule: String,
        documentType: CautionDocumentType,
    ): String {
        val sequence =
            jdbcClient
                .sql(
                    """
                    INSERT INTO caution_counter (id, last_value) VALUES (1, 1)
                    ON CONFLICT (id) DO UPDATE SET last_value = caution_counter.last_value + 1
                    RETURNING last_value
                    """.trimIndent(),
                ).query(Int::class.java)
                .single()
        val date = LocalDate.now(clock).format(dateFormat)
        return "%05d-%s-%s-%s".format(sequence, matricule, documentType.code, date)
    }
}
