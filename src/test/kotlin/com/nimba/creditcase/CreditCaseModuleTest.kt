package com.nimba.creditcase

import com.nimba.TestcontainersConfiguration
import com.nimba.client.ClientModuleApi
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import com.nimba.seedClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CreditCaseModuleTest(
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val users: UserRepository,
) {
    private fun seedAnalyst(): UUID {
        val user =
            users.saveAndFlush(
                User(
                    fullName = "Analyste Créateur",
                    email = "createur-${UUID.randomUUID()}@banque.test",
                    passwordHash = "hash",
                ),
            )
        return requireNotNull(user.id)
    }

    @Test
    fun `consecutive creations produce strictly increasing, unique case numbers`() {
        val analyst = seedAnalyst()

        val first =
            creditCases.createCase(
                CreateCreditCaseCommand(
                    seedClient(clients, "Client A"),
                    ProductType.LEASING,
                    "GNF",
                    analyst,
                    contractType = ContractType.AVEC_CONTRAT,
                ),
            )
        val second =
            creditCases.createCase(
                CreateCreditCaseCommand(
                    seedClient(clients, "Client B"),
                    ProductType.LEASING,
                    "GNF",
                    analyst,
                    contractType = ContractType.AVEC_CONTRAT,
                ),
            )

        assertTrue(first.caseNumber.matches(Regex("""DOS-\d{4}-\d{4}""")), "unexpected format: ${first.caseNumber}")
        assertTrue(second.caseNumber != first.caseNumber, "case numbers must be unique")
        assertTrue(second.caseNumber > first.caseNumber, "case numbers must be strictly increasing")
    }

    @Test
    fun `a created case is resolvable by id and by case number`() {
        val analyst = seedAnalyst()
        val created =
            creditCases.createCase(
                CreateCreditCaseCommand(
                    seedClient(clients, "Client Résolu"),
                    ProductType.LEASING,
                    "GNF",
                    analyst,
                    contractType = ContractType.AVEC_CONTRAT,
                ),
            )

        val byId = creditCases.findById(created.id)
        val byNumber = creditCases.findByCaseNumber(created.caseNumber)

        assertNotNull(byId)
        assertEquals(created.id, byId.id)
        assertNotNull(byNumber)
        assertEquals(created.id, byNumber.id)
        assertEquals(analyst, byNumber.createdBy)
    }

    @Test
    fun `creation is rejected when the creator is not a known analyst`() {
        assertFailsWith<ResponseStatusException> {
            creditCases.createCase(
                CreateCreditCaseCommand(UUID.randomUUID(), ProductType.LEASING, "GNF", UUID.randomUUID()),
            )
        }
    }
}
