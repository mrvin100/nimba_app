package com.nimba.guarantee

import com.nimba.TestcontainersConfiguration
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class GuaranteeModuleTest(
    @Autowired private val guarantees: GuaranteeModuleApi,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Autowired private val users: UserRepository,
) {
    private fun analystId(): UUID =
        requireNotNull(
            users
                .saveAndFlush(
                    User(fullName = "Analyste Garanties", email = "garanties-${UUID.randomUUID()}@banque.test", passwordHash = "hash"),
                ).id,
        )

    private fun caseId(analyst: UUID): UUID =
        creditCases
            .createCase(
                CreateCreditCaseCommand(
                    com.nimba.seedClient(clients, "Client Garanties"),
                    ProductType.LEASING,
                    "GNF",
                    analyst,
                    contractType = ContractType.AVEC_CONTRAT,
                ),
            ).id

    @Test
    fun `creates a guarantee attached to the case`() {
        val analyst = analystId()
        val id = caseId(analyst)

        val created = guarantees.create(CreateGuaranteeCommand(id, GuaranteeKind.A_RECUEILLIR, "Lettre de domiciliation", analyst))

        assertEquals(id, created.creditCaseId)
        assertEquals(GuaranteeKind.A_RECUEILLIR, created.kind)
        assertTrue(created.attachments.isEmpty())
    }

    @Test
    fun `creating a guarantee for an unknown case is rejected`() {
        assertFailsWith<ResponseStatusException> {
            guarantees.create(CreateGuaranteeCommand(UUID.randomUUID(), GuaranteeKind.DETENUE, "RAS", UUID.randomUUID()))
        }
    }

    @Test
    fun `updates and deletes a guarantee`() {
        val analyst = analystId()
        val id = caseId(analyst)
        val created = guarantees.create(CreateGuaranteeCommand(id, GuaranteeKind.A_RECUEILLIR, "Nantissement", analyst))

        val updated = guarantees.update(created.id, UpdateGuaranteeCommand(GuaranteeKind.DETENUE, "Nantissement signé"))
        assertEquals(GuaranteeKind.DETENUE, updated.kind)
        assertEquals("Nantissement signé", updated.description)

        guarantees.delete(created.id)
        assertNull(guarantees.findById(created.id))
    }

    @Test
    fun `lists a case's guarantees oldest first`() {
        val analyst = analystId()
        val id = caseId(analyst)
        val first = guarantees.create(CreateGuaranteeCommand(id, GuaranteeKind.DETENUE, "Premier", analyst))
        val second = guarantees.create(CreateGuaranteeCommand(id, GuaranteeKind.A_RECUEILLIR, "Second", analyst))

        val list = guarantees.listByCase(id)

        assertEquals(listOf(first.id, second.id), list.map { it.id })
    }

    @Test
    fun `deleting the case purges its guarantees`() {
        val analyst = analystId()
        val id = caseId(analyst)
        val created = guarantees.create(CreateGuaranteeCommand(id, GuaranteeKind.DETENUE, "RAS", analyst))

        creditCases.delete(id)

        assertNull(guarantees.findById(created.id))
    }
}
