package com.nimba.caution

import com.nimba.TestcontainersConfiguration
import com.nimba.client.ClientModuleApi
import com.nimba.client.CreateClientCommand
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

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CautionDossierTest(
    @Autowired private val cautions: CautionModuleApi,
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun dcmMemberId(): UUID =
        requireNotNull(seedMember(users, passwordEncoder, "dossier-${UUID.randomUUID()}@banque.test", Department.DCM).id)

    private fun clientId(dcm: UUID): UUID =
        clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "SOCIETE TEST", dcm, agence = "Kaloum")).id

    private val smsContent =
        mapOf(
            "beneficiaire" to "MINISTERE DE L'ELEVAGE",
            "referenceAppelOffres" to "N°01/MAGEL/DNAPA/PRMP/2026",
            "objetMarche" to "Travaux Lot 8",
            "devise" to "GNF",
            "montant" to "306000000",
            "dateEmission" to "2026-07-21",
            "dateOffre" to "2026-07-10",
            "dateExpiration" to "2026-10-21",
            "signataire1Nom" to "QUENTIN DETCHENOU",
            "signataire1Titre" to "Directeur Crédit Marketing",
            "signataire2Nom" to "FANNY SOUMAH",
            "signataire2Titre" to "Directrice Générale Adjointe",
        )

    @Test
    fun `opens a dossier and groups the documents attached to it`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)

        val dossier =
            cautions.createDossier(
                CreateDossierCommand(
                    clientId = client,
                    content = mapOf("beneficiaire" to "MINISTERE DE L'ELEVAGE", "referenceAppelOffres" to "N°01/MAGEL/2026"),
                    createdBy = dcm,
                ),
            )
        assertContains(dossier.referenceNumber, "-DOS-")
        assertEquals(DossierStatus.OPEN, dossier.status)

        val sms = cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm, dossierId = dossier.id))
        val afc = cautions.create(CreateCautionCommand(client, CautionDocumentType.AFC, smsContent, dcm, dossierId = dossier.id))
        // A standalone document (no dossier) must not show up under the dossier.
        cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))

        val documents = cautions.dossierDocuments(dossier.id)
        assertEquals(setOf(sms.id, afc.id), documents.map { it.id }.toSet())
    }

    @Test
    fun `rejects attaching a document to another client's dossier`() {
        val dcm = dcmMemberId()
        val clientA = clientId(dcm)
        val clientB = clientId(dcm)
        val dossier = cautions.createDossier(CreateDossierCommand(clientA, emptyMap(), dcm))

        assertFailsWith<ResponseStatusException> {
            cautions.create(CreateCautionCommand(clientB, CautionDocumentType.SMS, smsContent, dcm, dossierId = dossier.id))
        }
    }

    @Test
    fun `lists dossiers filtered by client`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        cautions.createDossier(CreateDossierCommand(client, emptyMap(), dcm))

        val forClient = cautions.listDossiers(PageRequest.of(0, 20), clientId = client)
        assertEquals(1, forClient.totalElements)
    }
}
