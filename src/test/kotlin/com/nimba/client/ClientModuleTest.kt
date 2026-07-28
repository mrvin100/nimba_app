package com.nimba.client

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.seedMember
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class ClientModuleTest(
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun dcmMemberId(): UUID =
        requireNotNull(seedMember(users, passwordEncoder, "client-${UUID.randomUUID()}@banque.test", Department.DCM).id)

    @Test
    fun `creates a client and finds it by matricule`() {
        val dcm = dcmMemberId()
        val matricule = "M-${UUID.randomUUID()}"

        val created =
            clients.create(
                CreateClientCommand(
                    matricule = matricule,
                    raisonSociale = "GUINEENNE DES TRAVAUX ET FOURNITURES - SARLU",
                    createdBy = dcm,
                    sigle = "G-TRAF +",
                    rccm = "GN.2025.B.07118",
                    accountNumber = "021 001 0103804401 34",
                    agence = "Kaloum",
                ),
            )

        assertEquals(matricule, created.matricule)
        val found = requireNotNull(clients.findByMatricule(matricule))
        assertEquals(created.id, found.id)
        assertEquals("G-TRAF +", found.sigle)
        assertEquals("GN.2025.B.07118", found.rccm)
    }

    @Test
    fun `a second client cannot reuse a matricule`() {
        val dcm = dcmMemberId()
        val matricule = "M-${UUID.randomUUID()}"
        clients.create(CreateClientCommand(matricule, "Client A", dcm))

        assertFailsWith<ResponseStatusException> {
            clients.create(CreateClientCommand(matricule, "Client B", dcm))
        }
    }

    @Test
    fun `a second client cannot reuse a codeNif`() {
        val dcm = dcmMemberId()
        val codeNif = "NIF-${UUID.randomUUID()}"
        clients.create(CreateClientCommand(raisonSociale = "Client A", createdBy = dcm, codeNif = codeNif))

        assertFailsWith<ResponseStatusException> {
            clients.create(CreateClientCommand(raisonSociale = "Client B", createdBy = dcm, codeNif = codeNif))
        }
    }

    @Test
    fun `updating a client to another client's codeNif is rejected`() {
        val dcm = dcmMemberId()
        val codeNif = "NIF-${UUID.randomUUID()}"
        clients.create(CreateClientCommand(raisonSociale = "Client A", createdBy = dcm, codeNif = codeNif))
        val clientB = clients.create(CreateClientCommand(raisonSociale = "Client B", createdBy = dcm))

        assertFailsWith<ResponseStatusException> {
            clients.update(clientB.id, UpdateClientCommand(raisonSociale = "Client B", codeNif = codeNif))
        }
    }

    @Test
    fun `a client keeps its own codeNif across an update`() {
        val dcm = dcmMemberId()
        val codeNif = "NIF-${UUID.randomUUID()}"
        val created = clients.create(CreateClientCommand(raisonSociale = "Client A", createdBy = dcm, codeNif = codeNif))

        val updated = clients.update(created.id, UpdateClientCommand(raisonSociale = "Client A corrigé", codeNif = codeNif))

        assertEquals(codeNif, updated.codeNif)
    }

    @Test
    fun `updates a client's descriptive details without touching its matricule`() {
        val dcm = dcmMemberId()
        val matricule = "M-${UUID.randomUUID()}"
        val created = clients.create(CreateClientCommand(matricule, "Nom initial", dcm))

        val updated =
            clients.update(
                created.id,
                UpdateClientCommand(raisonSociale = "Nom corrigé", dateEntreeRelation = LocalDate.of(2020, 1, 1)),
            )

        assertEquals(matricule, updated.matricule)
        assertEquals("Nom corrigé", updated.raisonSociale)
        assertEquals(LocalDate.of(2020, 1, 1), updated.dateEntreeRelation)
    }

    @Test
    fun `pages through clients`() {
        val dcm = dcmMemberId()
        repeat(3) { clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "Client $it", dcm)) }

        val page = clients.list(PageRequest.of(0, 2))

        assertEquals(2, page.content.size)
        assertEquals(2, page.size)
    }

    @Test
    fun `an unknown client is not found`() {
        assertNull(clients.findById(UUID.randomUUID()))
        assertFailsWith<ResponseStatusException> { clients.getOrThrow(UUID.randomUUID()) }
    }
}
