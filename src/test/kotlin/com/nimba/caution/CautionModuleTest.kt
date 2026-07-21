package com.nimba.caution

import com.nimba.TestcontainersConfiguration
import com.nimba.client.ClientModuleApi
import com.nimba.client.CreateClientCommand
import com.nimba.client.UpdateClientCommand
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
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CautionModuleTest(
    @Autowired private val cautions: CautionModuleApi,
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun dcmMemberId(): UUID =
        requireNotNull(seedMember(users, passwordEncoder, "caution-${UUID.randomUUID()}@banque.test", Department.DCM).id)

    private fun clientId(dcm: UUID): UUID =
        clients
            .create(
                CreateClientCommand(
                    matricule = "M-${UUID.randomUUID()}",
                    raisonSociale = "GUINEENNE DES TRAVAUX ET FOURNITURES - SARLU",
                    createdBy = dcm,
                    rccm = "GN.2025.B.07118",
                    accountNumber = "021 001 0103804401 34",
                    agence = "Kaloum",
                ),
            ).id

    private val smsContent =
        mapOf(
            "beneficiaire" to "ELECTRICITE DE GUINEE EDG-SA",
            "referenceAppelOffres" to "AAONO N°: 001/EDG-SA/DAAL/PRMP/2026",
            "objetMarche" to "Travaux de réfection des bâtiments du site de Garafiri",
            "montant" to "238756476",
            "dateEmission" to "2026-02-11",
            "dateOffre" to "2026-02-13",
            "dateExpiration" to "2026-05-13",
        )

    @Test
    fun `creates a draft SMS caution with an assigned reference number`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)

        val created = cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))

        assertEquals(CautionStatus.DRAFT, created.status)
        assertContains(created.referenceNumber, "-SMS-")
        assertNull(created.clientSnapshot)
        assertEquals("238756476", created.content["montant"])
    }

    @Test
    fun `rejects a caution missing a required field for its document type`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)

        assertFailsWith<ResponseStatusException> {
            cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, mapOf("beneficiaire" to "EDG-SA"), dcm))
        }
    }

    @Test
    fun `two documents for the same client draw independent reference numbers from the same global sequence`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val acfContent =
            mapOf(
                "beneficiaire" to "ELECTRICITE DE GUINEE EDG SA",
                "referenceAppelOffres" to "007/EDG-SA/DAAL/DPSM/2025",
                "objetMarche" to "Travaux de réfection des bâtiments de GARAFIRI",
                "montant" to "2828096140",
                "dateEmission" to "2026-02-19",
            )

        val sms = cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))
        val acf = cautions.create(CreateCautionCommand(client, CautionDocumentType.ACF, acfContent, dcm))

        assertTrue(sms.referenceNumber != acf.referenceNumber)
        val smsSequence = sms.referenceNumber.substringBefore("-").toInt()
        val acfSequence = acf.referenceNumber.substringBefore("-").toInt()
        assertEquals(smsSequence + 1, acfSequence)
    }

    @Test
    fun `finalizing freezes the client snapshot, immune to later client edits`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val created = cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))

        val finalized = cautions.finalize(created.id)
        assertEquals(CautionStatus.FINAL, finalized.status)
        val snapshot = requireNotNull(finalized.clientSnapshot)
        assertEquals("Kaloum", snapshot.agence)

        clients.update(client, UpdateClientCommand(raisonSociale = "Nouveau nom", agence = "Autre agence"))
        val reloaded = requireNotNull(cautions.findById(created.id))
        assertEquals("Kaloum", requireNotNull(reloaded.clientSnapshot).agence)
    }

    @Test
    fun `a finalized caution rejects further edits, deletion or re-finalization`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val created = cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))
        cautions.finalize(created.id)

        assertFailsWith<ResponseStatusException> { cautions.update(created.id, UpdateCautionCommand(smsContent)) }
        assertFailsWith<ResponseStatusException> { cautions.finalize(created.id) }
        assertFailsWith<ResponseStatusException> { cautions.delete(created.id) }
    }

    @Test
    fun `deletes a draft`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val created = cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))

        cautions.delete(created.id)

        assertNull(cautions.findById(created.id))
    }

    @Test
    fun `lists and filters cautions by client and document type`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val otherClient = clientId(dcm)
        cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))
        cautions.create(CreateCautionCommand(otherClient, CautionDocumentType.SMS, smsContent, dcm))

        val forClient = cautions.list(PageRequest.of(0, 20), clientId = client)
        assertEquals(1, forClient.totalElements)

        // Scoped by this test's own client — the table isn't isolated per test class,
        // so a bare "no ACF anywhere" assertion would be flaky against other tests' data.
        val byType = cautions.list(PageRequest.of(0, 20), clientId = client, documentType = CautionDocumentType.ACF)
        assertEquals(0, byType.totalElements)
    }
}
