package com.nimba.identity

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["nimba.mail.enabled=false"],
)
class AdminStatsEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun req(path: String) = HttpRequest.newBuilder(URI("http://localhost:$port$path"))

    private fun send(
        client: HttpClient,
        request: HttpRequest,
    ) = client.send(request, HttpResponse.BodyHandlers.ofString())

    private fun adminClient(email: String): HttpClient {
        if (users.findByEmail(email) == null) {
            users.saveAndFlush(User("Test $email", email, passwordEncoder.encode("Pass-Word")).apply { platformAdmin = true })
        }
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        send(
            client,
            req("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"Pass-Word"}"""))
                .build(),
        )
        return client
    }

    @Test
    fun `user stats report totals and a per-direction breakdown`() {
        val admin = adminClient("stats-admin@nimba.test")

        val response = send(admin, req("/api/v1/admin/stats/users").GET().build())

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "\"total\"")
        assertContains(response.body(), "\"byDepartment\"")
        assertContains(response.body(), "DRI")
        assertContains(response.body(), "DCM")
        assertContains(response.body(), "DRC")
    }

    @Test
    fun `credit-case stats report totals and a per-status breakdown`() {
        val admin = adminClient("stats-admin2@nimba.test")

        val response = send(admin, req("/api/v1/admin/stats/dossiers").GET().build())

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "\"total\"")
        assertContains(response.body(), "EN_ATTENTE_AMORTISSEMENT")
        assertContains(response.body(), "TRADES_GENERES")
    }

    @Test
    fun `the audit trail accepts period, method and status filters`() {
        val admin = adminClient("stats-admin3@nimba.test")

        val response =
            send(admin, req("/api/v1/admin/audit?from=2020-01-01&to=2030-12-31&method=POST&status=200").GET().build())

        assertEquals(200, response.statusCode(), response.body())
        assertContains(response.body(), "\"content\"")
    }

    @Test
    fun `a non-admin is forbidden from the stats endpoints`() {
        if (users.findByEmail("stats-member@nimba.test") == null) {
            users.saveAndFlush(
                User("Membre", "stats-member@nimba.test", passwordEncoder.encode("Pass-Word"))
                    .apply { assign(Department.DRI, DepartmentRole.MEMBER) },
            )
        }
        val member = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        send(
            member,
            req("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"stats-member@nimba.test","password":"Pass-Word"}"""))
                .build(),
        )

        assertEquals(403, send(member, req("/api/v1/admin/stats/users").GET().build()).statusCode())
    }
}
