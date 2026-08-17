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
 * A client's matricule is baked into a caution document's reference number
 * once, at creation. When a manager corrects a mistyped matricule afterward,
 * still-draft document references realign to match; anything already
 * finalized keeps the matricule it was issued with, since it has its own
 * frozen client snapshot. A dossier's own reference number never embeds the
 * matricule at all, so it is never touched by this.
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
            "numeroCompte" to "0101788201 05",
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
    fun `correcting the matricule realigns a draft document's reference but leaves a finalized one and the dossier alone`() {
        val dcm = dcmMemberId()
        val oldMatricule = "M-OLD-${UUID.randomUUID()}"
        val newMatricule = "M-NEW-${UUID.randomUUID()}"
        val client = clients.create(CreateClientCommand(oldMatricule, "SOCIETE TEST", dcm, agence = "Kaloum")).id

        val dossier = cautions.createDossier(CreateDossierCommand(client, emptyMap(), dcm, cautions.suggestNextDossierSequence()))
        val draftDocument =
            cautions.create(
                CreateCautionCommand(
                    client,
                    CautionDocumentType.SMS,
                    smsContent,
                    dcm,
                    cautions.suggestNextSequence(CautionDocumentType.SMS),
                ),
            )
        val finalizedDocument =
            cautions.create(
                CreateCautionCommand(
                    client,
                    CautionDocumentType.AFC,
                    smsContent,
                    dcm,
                    cautions.suggestNextSequence(CautionDocumentType.AFC),
                ),
            )
        cautions.finalize(finalizedDocument.id)

        assertContains(draftDocument.referenceNumber, oldMatricule)

        clients.updateMatricule(client, UpdateClientMatriculeCommand(newMatricule))

        val realignedDocument = requireNotNull(cautions.findById(draftDocument.id))
        assertContains(realignedDocument.referenceNumber, newMatricule)

        val untouchedDocument = requireNotNull(cautions.findById(finalizedDocument.id))
        assertEquals(finalizedDocument.referenceNumber, untouchedDocument.referenceNumber)
        assertContains(untouchedDocument.referenceNumber, oldMatricule)

        // The dossier's own reference never embedded a matricule, so a correction never touches it.
        val untouchedDossier = requireNotNull(cautions.findDossier(dossier.id))
        assertEquals(dossier.referenceNumber, untouchedDossier.referenceNumber)
    }
}
