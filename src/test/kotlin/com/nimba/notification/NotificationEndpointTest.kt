package com.nimba.notification

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.notification.internal.NotificationService
import com.nimba.seedMember
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
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NotificationEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val notifications: NotificationService,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun clientFor(email: String): HttpClient {
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""{"email":"$email","password":"Pass-Word"}"""))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return client
    }

    private fun get(
        client: HttpClient,
        path: String,
    ): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI("http://localhost:$port/api/v1$path")).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun post(
        client: HttpClient,
        path: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port/api/v1$path")).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `lists, counts and marks a notification read over HTTP`() {
        val recipient = requireNotNull(seedMember(users, passwordEncoder, "notif-http@banque.test", Department.DRI).id)
        notifications.notifyUser(recipient, UUID.randomUUID(), "Dossier à revoir")
        val client = clientFor("notif-http@banque.test")

        val unread = get(client, "/notifications/unread-count")
        assertEquals(200, unread.statusCode())
        assertContains(unread.body(), "\"count\":1")

        val list = get(client, "/notifications")
        assertEquals(200, list.statusCode())
        assertContains(list.body(), "Dossier à revoir")
        val id = Regex(""""id":"([0-9a-fA-F-]{36})"""").find(list.body())!!.groupValues[1]

        val markRead = post(client, "/notifications/$id/read")
        assertEquals(200, markRead.statusCode(), markRead.body())
        assertContains(markRead.body(), "\"read\":true")

        assertContains(get(client, "/notifications/unread-count").body(), "\"count\":0")
    }

    @Test
    fun `mark-all-read clears every unread notification`() {
        val recipient = requireNotNull(seedMember(users, passwordEncoder, "notif-http-all@banque.test", Department.DCM).id)
        notifications.notifyUser(recipient, null, "Un")
        notifications.notifyUser(recipient, null, "Deux")
        val client = clientFor("notif-http-all@banque.test")

        val response = post(client, "/notifications/read-all")
        assertEquals(200, response.statusCode())
        assertContains(get(client, "/notifications/unread-count").body(), "\"count\":0")
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        assertEquals(401, get(anonymous, "/notifications").statusCode())
    }
}
