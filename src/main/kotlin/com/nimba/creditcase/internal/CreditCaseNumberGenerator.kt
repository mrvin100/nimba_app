package com.nimba.creditcase.internal

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

/**
 * Generates the human-readable case number `DOS-{year}-{NNNN}`, sequential and
 * unique within a year. The next value is produced by an atomic per-year upsert on
 * a dedicated counter table, so concurrent creations cannot collide or skip — a
 * plain `SELECT max(...) + 1` would race. The number is zero-padded to four digits.
 */
@Component
class CreditCaseNumberGenerator(
    private val jdbcClient: JdbcClient,
    private val clock: Clock,
) {
    @Transactional
    fun nextCaseNumber(): String {
        val year = LocalDate.now(clock).year
        val sequence =
            jdbcClient
                .sql(
                    """
                    INSERT INTO credit_case_counter (year, last_value) VALUES (:year, 1)
                    ON CONFLICT (year) DO UPDATE SET last_value = credit_case_counter.last_value + 1
                    RETURNING last_value
                    """.trimIndent(),
                ).param("year", year)
                .query(Int::class.java)
                .single()
        return "DOS-%d-%04d".format(year, sequence)
    }
}
