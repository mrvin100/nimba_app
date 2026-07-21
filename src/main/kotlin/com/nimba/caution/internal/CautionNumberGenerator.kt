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
 *
 * [startingSequence] lets the very first caution ever created continue the
 * bank's own pre-existing paper numbering instead of restarting at 1 — it
 * only has an effect the first time (the INSERT branch); once the counter
 * row exists, every later call falls into the ON CONFLICT branch and simply
 * increments, ignoring the parameter, exactly as the plain sequential logic
 * requires from then on.
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
        startingSequence: Int? = null,
    ): String {
        val sequence =
            jdbcClient
                .sql(
                    """
                    INSERT INTO caution_counter (id, last_value) VALUES (1, :start)
                    ON CONFLICT (id) DO UPDATE SET last_value = caution_counter.last_value + 1
                    RETURNING last_value
                    """.trimIndent(),
                ).param("start", startingSequence ?: 1)
                .query(Int::class.java)
                .single()
        val date = LocalDate.now(clock).format(dateFormat)
        return "%05d-%s-%s-%s".format(sequence, matricule, documentType.code, date)
    }

    /** Whether any caution has ever been created — the frontend only offers [nextReferenceNumber]'s starting-sequence override before this becomes true. */
    @Transactional(readOnly = true)
    fun isInitialized(): Boolean =
        jdbcClient
            .sql("SELECT count(*) FROM caution_counter WHERE id = 1")
            .query(Int::class.java)
            .single() > 0
}
