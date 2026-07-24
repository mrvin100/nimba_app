package com.nimba.creditcase

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertEquals

/**
 * Exercises the V36 data migration (credit_case → Client) on real legacy rows — the
 * regular suite runs every migration on an empty database, so the data-move logic is
 * never otherwise executed. Flyway is driven in two phases: to V35 (schema still
 * carrying the legacy client_name + embedded identity), legacy dossiers are inserted,
 * then V36 migrates them into deduplicated Client rows.
 */
@Testcontainers
class ClientUnificationMigrationTest {
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
    fun `legacy dossiers become clients, deduplicated by code_nif`() {
        // Schema up to V35 (client.type added); credit_case still carries the legacy
        // client_name + embedded identity columns V36 will migrate then drop.
        flywayTo("35")

        connection().use { conn ->
            insertLegacyCase(conn, "DOS-2026-0001", "ETS ALPHA", codeNif = "NIF-1", formeJuridique = "SARL")
            insertLegacyCase(conn, "DOS-2026-0002", "ETS ALPHA BIS", codeNif = "NIF-1", formeJuridique = "SARL")
            insertLegacyCase(conn, "DOS-2026-0003", "ETS BETA", codeNif = null, formeJuridique = "SA")
        }

        flywayTo("36")

        connection().use { conn ->
            assertEquals(2, count(conn, "SELECT count(*) FROM client"), "two nif groups -> two clients")
            assertEquals(0, count(conn, "SELECT count(*) FROM credit_case WHERE client_id IS NULL"), "every dossier is linked")
            assertEquals(
                1,
                count(
                    conn,
                    "SELECT count(DISTINCT cc.client_id) FROM credit_case cc " +
                        "JOIN client cl ON cl.id = cc.client_id WHERE cl.code_nif = 'NIF-1'",
                ),
                "the two NIF-1 dossiers share one client",
            )
            assertEquals("SARL", text(conn, "SELECT forme_juridique FROM client WHERE code_nif = 'NIF-1'"), "identity preserved")
            assertEquals(
                "ENTREPRISE",
                text(conn, "SELECT type FROM client WHERE code_nif = 'NIF-1'"),
                "migrated clients default to ENTREPRISE",
            )
        }
    }

    private fun insertLegacyCase(
        conn: Connection,
        caseNumber: String,
        clientName: String,
        codeNif: String?,
        formeJuridique: String,
    ) {
        conn
            .prepareStatement(
                "INSERT INTO credit_case (id, case_number, client_name, product_type, currency, " +
                    "code_nif, forme_juridique, created_by, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'LEASING', 'GNF', ?, ?, ?, now(), now())",
            ).use { ps ->
                ps.setObject(1, UUID.randomUUID())
                ps.setString(2, caseNumber)
                ps.setString(3, clientName)
                ps.setString(4, codeNif)
                ps.setString(5, formeJuridique)
                ps.setObject(6, UUID.randomUUID())
                ps.executeUpdate()
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

    private fun text(
        conn: Connection,
        sql: String,
    ): String =
        conn.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                rs.next()
                rs.getString(1)
            }
        }
}
