package com.nimba.amortizationschedule

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import java.io.File
import java.net.CookieManager
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@TestPropertySource(properties = ["storage.amortization-schedule-dir=build/test-storage/credit-cases"])
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScheduleUploadEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Value("\${local.server.port}") private val port: Int,
) {
    private val boundary = "----NimbaUploadBoundary"

    private fun analystId(): UUID {
        val existing = users.findByEmail("upload@banque.test")
        return existing?.id ?: requireNotNull(
            users
                .saveAndFlush(User("Analyste Upload", "upload@banque.test", requireNotNull(passwordEncoder.encode("Pass-Word"))))
                .id,
        )
    }

    private fun authenticatedClient(): HttpClient {
        analystId()
        val client = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val login =
            client.send(
                HttpRequest
                    .newBuilder(URI("http://localhost:$port/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("""{"email":"upload@banque.test","password":"Pass-Word"}"""))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        assertEquals(200, login.statusCode())
        return client
    }

    private fun newCaseId(): UUID =
        creditCases.createCase(CreateCreditCaseCommand("Client Upload", ProductType.LEASING, "GNF", analystId())).id

    private fun upload(
        client: HttpClient,
        caseId: UUID,
        content: ByteArray,
    ): HttpResponse<String> {
        val head =
            (
                "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"echeancier.csv\"\r\n" +
                    "Content-Type: text/csv\r\n\r\n"
            ).toByteArray(Charsets.UTF_8)
        val body = head + content + "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        return client.send(
            HttpRequest
                .newBuilder(URI("http://localhost:$port/api/v1/credit-cases/$caseId/amortization-schedule"))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    @Test
    fun `two successive valid uploads produce versions 1 then 2`() {
        val client = authenticatedClient()
        val caseId = newCaseId()
        val valid = File("docs/examples/exemple-echeancier-valide.csv").readBytes()

        val first = upload(client, caseId, valid)
        assertEquals(201, first.statusCode(), first.body())
        assertContains(first.body(), "\"versionNumber\":1")

        val second = upload(client, caseId, valid)
        assertEquals(201, second.statusCode(), second.body())
        assertContains(second.body(), "\"versionNumber\":2")
    }

    @Test
    fun `an erroneous file is rejected with 422 and persists nothing`() {
        val client = authenticatedClient()
        val caseId = newCaseId()
        val invalid = File("docs/examples/exemple-echeancier-invalide.csv").readBytes()

        val response = upload(client, caseId, invalid)

        assertEquals(422, response.statusCode())
        assertNull(
            schedules.findFirstByCreditCaseIdOrderByVersionNumberDesc(caseId),
            "no schedule must remain after a rejected upload",
        )
    }

    @Test
    fun `rejects upload without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        val caseId = newCaseId()

        val response = upload(anonymous, caseId, "x".toByteArray())

        assertEquals(401, response.statusCode())
    }
}
