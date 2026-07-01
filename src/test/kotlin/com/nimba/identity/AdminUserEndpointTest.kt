package com.nimba.identity

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.InvitationRepository
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
class AdminUserEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val invitations: InvitationRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun seed(
        email: String,
        configure: (User) -> Unit,
    ) {
        if (users.findByEmail(email) != null) return
        users.saveAndFlush(User("Test $email", email, requireNotNull(passwordEncoder.encode("Pass-Word"))).apply(configure))
    }

    private fun clientFor(
        email: String,
        password: String = "Pass-Word",
    ): HttpClient {
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            req("/api/v1/auth/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"$password"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun req(path: String) = HttpRequest.newBuilder(URI("http://localhost:$port$path"))

    private fun send(
        client: HttpClient,
        request: HttpRequest,
    ) = client.send(request, HttpResponse.BodyHandlers.ofString())

    private fun login(
        email: String,
        password: String,
    ) = send(
        HttpClient.newBuilder().cookieHandler(CookieManager()).build(),
        req("/api/v1/auth/login")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"$password"}"""))
            .build(),
    )

    @Test
    fun `admin invites a user who activates via set-password, then is suspended`() {
        seed("admin@nimba.test") { it.platformAdmin = true }
        val admin = clientFor("admin@nimba.test")

        val create =
            send(
                admin,
                req("/api/v1/admin/users")
                    .header("Content-Type", "application/json")
                    .POST(
                        HttpRequest.BodyPublishers.ofString(
                            """{"fullName":"Nouvel Analyste","email":"created@nimba.test",
                               "admin":false,"memberships":[{"department":"DRI","role":"MEMBER"}]}""",
                        ),
                    ).build(),
            )
        assertEquals(201, create.statusCode(), create.body())
        assertContains(create.body(), "created@nimba.test")
        assertContains(create.body(), "\"pending\":true")

        val list = send(admin, req("/api/v1/admin/users").GET().build())
        assertEquals(200, list.statusCode())
        assertContains(list.body(), "created@nimba.test")

        // No password yet: the invited user cannot log in.
        assertEquals(401, login("created@nimba.test", "New-Password-1").statusCode())

        // Consume the invitation to set the password, then login succeeds.
        val userId = requireNotNull(users.findByEmail("created@nimba.test")?.id)
        val token = requireNotNull(invitations.findAll().first { it.userId == userId }.token)
        val setPassword =
            send(
                HttpClient.newBuilder().build(),
                req("/api/v1/auth/set-password")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"token":"$token","password":"New-Password-1"}"""))
                    .build(),
            )
        assertEquals(200, setPassword.statusCode(), setPassword.body())

        val newUser = clientFor("created@nimba.test", "New-Password-1")
        assertEquals(200, send(newUser, req("/api/v1/auth/me").GET().build()).statusCode())

        // Admin suspends them; a suspended account can no longer authenticate.
        val suspend = send(admin, req("/api/v1/admin/users/$userId/suspend").POST(HttpRequest.BodyPublishers.noBody()).build())
        assertEquals(200, suspend.statusCode())
        assertEquals(401, login("created@nimba.test", "New-Password-1").statusCode())
    }

    @Test
    fun `an admin cannot suspend their own account`() {
        seed("self-admin@nimba.test") { it.platformAdmin = true }
        val admin = clientFor("self-admin@nimba.test")
        val id = requireNotNull(users.findByEmail("self-admin@nimba.test")?.id)

        val response = send(admin, req("/api/v1/admin/users/$id/suspend").POST(HttpRequest.BodyPublishers.noBody()).build())

        assertEquals(400, response.statusCode(), response.body())
    }

    @Test
    fun `admin actions are recorded in the audit trail`() {
        seed("audit-admin@nimba.test") { it.platformAdmin = true }
        val admin = clientFor("audit-admin@nimba.test")

        send(
            admin,
            req("/api/v1/admin/users")
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        """{"fullName":"Audité","email":"audited@nimba.test","admin":false,
                           "memberships":[{"department":"DRI","role":"MEMBER"}]}""",
                    ),
                ).build(),
        )

        val audit = send(admin, req("/api/v1/admin/audit").GET().build())
        assertEquals(200, audit.statusCode(), audit.body())
        assertContains(audit.body(), "Création d'un utilisateur")
    }

    @Test
    fun `a non-admin is forbidden from the admin API`() {
        seed("member@nimba.test") { it.assign(Department.DRI, DepartmentRole.MEMBER) }
        val member = clientFor("member@nimba.test")

        val response = send(member, req("/api/v1/admin/users").GET().build())

        assertEquals(403, response.statusCode())
    }

    @Test
    fun `the admin API rejects an unauthenticated request`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val response = send(anonymous, req("/api/v1/admin/users").GET().build())
        assertEquals(401, response.statusCode())
    }
}
