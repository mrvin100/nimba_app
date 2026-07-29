package com.nimba.caution

import com.nimba.TestcontainersConfiguration
import com.nimba.client.ClientModuleApi
import com.nimba.client.CreateClientCommand
import com.nimba.client.UpdateClientMatriculeCommand
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.seedMember
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * A client's matricule is baked into a caution reference number once, at
 * creation. When a manager corrects a mistyped matricule afterward, still-draft
 * references realign to match; anything already finalized keeps the matricule
 * it was issued with, since it has its own frozen client snapshot.
 */
@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CautionMatriculeRealignmentTest(
    @Autowired private val cautions: CautionModuleApi,
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun dcmMemberId(): UUID =
        requireNotNull(seedMember(users, passwordEncoder, "realign-${UUID.randomUUID()}@banque.test", Department.DCM).id)

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
    fun `correcting the matricule realigns draft references but leaves finalized ones alone`() {
        val dcm = dcmMemberId()
        val oldMatricule = "M-OLD-${UUID.randomUUID()}"
        val newMatricule = "M-NEW-${UUID.randomUUID()}"
        val client = clients.create(CreateClientCommand(oldMatricule, "SOCIETE TEST", dcm, agence = "Kaloum")).id

        val draftDossier = cautions.createDossier(CreateDossierCommand(client, emptyMap(), dcm))
        val draftDocument = cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm))

        val finalizedDossier = cautions.createDossier(CreateDossierCommand(client, emptyMap(), dcm))
        cautions.finalizeDossier(finalizedDossier.id, dcm)
        val finalizedDocument = cautions.create(CreateCautionCommand(client, CautionDocumentType.AFC, smsContent, dcm))
        cautions.finalize(finalizedDocument.id)

        assertContains(draftDossier.referenceNumber, oldMatricule)
        assertContains(draftDocument.referenceNumber, oldMatricule)

        clients.updateMatricule(client, UpdateClientMatriculeCommand(newMatricule))

        val realignedDossier = requireNotNull(cautions.findDossier(draftDossier.id))
        val realignedDocument = requireNotNull(cautions.findById(draftDocument.id))
        assertContains(realignedDossier.referenceNumber, newMatricule)
        assertContains(realignedDocument.referenceNumber, newMatricule)

        val untouchedDossier = requireNotNull(cautions.findDossier(finalizedDossier.id))
        val untouchedDocument = requireNotNull(cautions.findById(finalizedDocument.id))
        assertEquals(finalizedDossier.referenceNumber, untouchedDossier.referenceNumber)
        assertContains(untouchedDossier.referenceNumber, oldMatricule)
        assertEquals(finalizedDocument.referenceNumber, untouchedDocument.referenceNumber)
        assertContains(untouchedDocument.referenceNumber, oldMatricule)
    }
}
