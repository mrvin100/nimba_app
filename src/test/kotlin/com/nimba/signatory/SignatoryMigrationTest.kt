package com.nimba.signatory

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the V37 data migration (organization_settings' two frozen signatory slots
 * → standalone Signatory rows) on a real pre-existing row, then confirms the old
 * columns are gone. Two-phase: schema to V36 (before signatories), seed the legacy
 * columns, then migrate to V37.
 */
@Testcontainers
class SignatoryMigrationTest {
    companion object {
        @Container
        @JvmStatic
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:18"))
    }

    private fun flywayTo(target: String) {
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration")
            .target(target)
            .load()
            .migrate()
    }

    private fun connection(): Connection = DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    @Test
    fun `configured signatories become standalone rows, and the legacy columns are dropped`() {
        flywayTo("36")

        connection().use { conn ->
            conn
                .createStatement()
                .use { st ->
                    st.executeUpdate(
                        "UPDATE organization_settings SET signataire1_nom = 'Jean Dupont', " +
                            "signataire1_titre = 'Directeur Credit Marketing', " +
                            "signataire2_nom = 'Marie Camara', signataire2_titre = NULL " +
                            "WHERE id = 1",
                    )
                }
        }

        flywayTo("37")

        connection().use { conn ->
            assertEquals(2, count(conn, "SELECT count(*) FROM signatory"))
            assertEquals(
                1,
                count(conn, "SELECT count(*) FROM signatory WHERE nom = 'Jean Dupont' AND titre = 'Directeur Credit Marketing'"),
            )
            assertEquals(1, count(conn, "SELECT count(*) FROM signatory WHERE nom = 'Marie Camara' AND titre = 'RAS'"))
            assertEquals(0, count(conn, "SELECT count(*) FROM signatory_authorization"), "migrated rows stay global")
            assertNull(columnExistsOrNull(conn), "the legacy organization_settings signatory columns must be gone")
        }
    }

    private fun count(
        conn: Connection,
        sql: String,
    ): Int =
        conn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                rs.next()
                rs.getInt(1)
            }
        }

    /** Returns null (as expected) when none of the four legacy columns exist anymore. */
    private fun columnExistsOrNull(conn: Connection): String? =
        conn.createStatement().use { st ->
            st
                .executeQuery(
                    "SELECT column_name FROM information_schema.columns " +
                        "WHERE table_name = 'organization_settings' AND column_name LIKE 'signataire%'",
                ).use { rs -> if (rs.next()) rs.getString(1) else null }
        }
}
