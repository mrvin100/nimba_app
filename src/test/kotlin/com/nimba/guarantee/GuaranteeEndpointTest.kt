package com.nimba.guarantee

import com.nimba.TestcontainersConfiguration
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
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
class GuaranteeEndpointTest(
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Value("\${local.server.port}") private val port: Int,
) {
    private fun member(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

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

    private fun path(
        caseId: UUID,
        suffix: String = "",
    ) = "http://localhost:$port/api/v1/credit-cases/$caseId/guarantees$suffix"

    private fun get(
        client: HttpClient,
        url: String,
    ): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI(url)).GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun post(
        client: HttpClient,
        url: String,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun put(
        client: HttpClient,
        url: String,
        body: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(URI(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun delete(
        client: HttpClient,
        url: String,
    ): HttpResponse<String> = client.send(HttpRequest.newBuilder(URI(url)).DELETE().build(), HttpResponse.BodyHandlers.ofString())

    private fun newCaseId(driId: UUID): UUID =
        creditCases
            .createCase(
                CreateCreditCaseCommand(
                    "Client Garanties HTTP",
                    ProductType.LEASING,
                    "GNF",
                    driId,
                    contractType = ContractType.AVEC_CONTRAT,
                ),
            ).id

    @Test
    fun `DRI creates, lists, updates and deletes a guarantee`() {
        val driId = member("http-guar-dri@banque.test", Department.DRI)
        val caseId = newCaseId(driId)
        val dri = clientFor("http-guar-dri@banque.test")

        val created =
            post(dri, path(caseId), """{"kind":"A_RECUEILLIR","description":"Lettre de domiciliation"}""")
        assertEquals(201, created.statusCode(), created.body())
        assertContains(created.body(), "A_RECUEILLIR")
        val id = Regex(""""id":"([0-9a-fA-F-]{36})"""").find(created.body())!!.groupValues[1]

        val list = get(dri, path(caseId))
        assertEquals(200, list.statusCode())
        assertContains(list.body(), "Lettre de domiciliation")

        val updated = put(dri, path(caseId, "/$id"), """{"kind":"DETENUE","description":"Lettre signée"}""")
        assertEquals(200, updated.statusCode(), updated.body())
        assertContains(updated.body(), "Lettre signée")

        val deleted = delete(dri, path(caseId, "/$id"))
        assertEquals(204, deleted.statusCode())
        assertEquals("[]", get(dri, path(caseId)).body().trim())
    }

    @Test
    fun `a DCM reviewer can read guarantees but not create one`() {
        val driId = member("http-guar-dri2@banque.test", Department.DRI)
        member("http-guar-dcm@banque.test", Department.DCM)
        val caseId = newCaseId(driId)

        val dcm = clientFor("http-guar-dcm@banque.test")
        assertEquals(200, get(dcm, path(caseId)).statusCode())

        val forbidden = post(dcm, path(caseId), """{"kind":"DETENUE","description":"RAS"}""")
        assertEquals(403, forbidden.statusCode())
    }

    @Test
    fun `a guarantee is not reachable through a different case's path`() {
        val driId = member("http-guar-dri3@banque.test", Department.DRI)
        val caseId = newCaseId(driId)
        val otherCaseId = newCaseId(driId)
        val dri = clientFor("http-guar-dri3@banque.test")
        val created = post(dri, path(caseId), """{"kind":"DETENUE","description":"RAS"}""")
        val id = Regex(""""id":"([0-9a-fA-F-]{36})"""").find(created.body())!!.groupValues[1]

        val response = put(dri, path(otherCaseId, "/$id"), """{"kind":"DETENUE","description":"Modifié"}""")

        assertEquals(404, response.statusCode())
    }

    @Test
    fun `rejects without an authenticated session`() {
        val anonymous = HttpClient.newBuilder().cookieHandler(CookieManager()).build()
        assertEquals(401, get(anonymous, path(UUID.randomUUID())).statusCode())
    }
}
