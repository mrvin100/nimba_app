package com.nimba.caution

import com.nimba.TestcontainersConfiguration
import com.nimba.caution.internal.CautionDocxExportService
import com.nimba.client.ClientModuleApi
import com.nimba.client.CreateClientCommand
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.seedMember
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CautionDossierTest(
    @Autowired private val cautions: CautionModuleApi,
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val export: CautionDocxExportService,
) {
    private fun dcmMemberId(): UUID =
        requireNotNull(seedMember(users, passwordEncoder, "dossier-${UUID.randomUUID()}@banque.test", Department.DCM).id)

    private fun clientId(dcm: UUID): UUID =
        clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "SOCIETE TEST", dcm, agence = "Kaloum")).id

    /** Every call gets a fresh, always-free sequence in its series, same as the create form's suggestion. */
    private fun nextSequence(type: CautionDocumentType): Int = cautions.suggestNextSequence(type)

    private fun nextDossierSequence(): Int = cautions.suggestNextDossierSequence()

    private fun dossierCommand(
        clientId: UUID,
        content: Map<String, String>,
        createdBy: UUID,
    ): CreateDossierCommand = CreateDossierCommand(clientId, content, createdBy, nextDossierSequence())

    /** Attaches a document of [type] to [dossierId] with a fresh sequence in its own series and the shared smsContent fixture. */
    private fun attachDocument(
        client: UUID,
        type: CautionDocumentType,
        dcm: UUID,
        dossierId: UUID,
    ): CautionInfo =
        cautions.create(
            CreateCautionCommand(client, type, smsContent, dcm, nextSequence(type), dossierId = dossierId),
        )

    private fun docText(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            buildString {
                doc.paragraphs.forEach { appendLine(it.text) }
                doc.tables.forEach { t -> t.rows.forEach { r -> r.tableCells.forEach { appendLine(it.text) } } }
            }
        }

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
    fun `opens a dossier and groups the documents attached to it`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)

        val dossier =
            cautions.createDossier(
                dossierCommand(
                    client,
                    mapOf("beneficiaire" to "MINISTERE DE L'ELEVAGE", "referenceAppelOffres" to "N°01/MAGEL/2026"),
                    dcm,
                ),
            )
        assertTrue(dossier.referenceNumber.startsWith("DOS-"))
        assertEquals(DossierStatus.BROUILLON, dossier.status)

        val sms = attachDocument(client, CautionDocumentType.SMS, dcm, dossier.id)
        val afc = attachDocument(client, CautionDocumentType.AFC, dcm, dossier.id)
        // A standalone document (no dossier) must not show up under the dossier.
        cautions.create(CreateCautionCommand(client, CautionDocumentType.SMS, smsContent, dcm, nextSequence(CautionDocumentType.SMS)))

        val documents = cautions.dossierDocuments(dossier.id)
        assertEquals(setOf(sms.id, afc.id), documents.map { it.id }.toSet())
    }

    @Test
    fun `rejects attaching a document to another client's dossier`() {
        val dcm = dcmMemberId()
        val clientA = clientId(dcm)
        val clientB = clientId(dcm)
        val dossier = cautions.createDossier(dossierCommand(clientA, emptyMap(), dcm))

        assertFailsWith<ResponseStatusException> {
            attachDocument(clientB, CautionDocumentType.SMS, dcm, dossier.id)
        }
    }

    @Test
    fun `lists dossiers filtered by client`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        cautions.createDossier(dossierCommand(client, emptyMap(), dcm))

        val forClient = cautions.listDossiers(PageRequest.of(0, 20), clientId = client)
        assertEquals(1, forClient.totalElements)
    }

    @Test
    fun `deleting a dossier removes it and cascades to its documents`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val dossier = cautions.createDossier(dossierCommand(client, emptyMap(), dcm))
        attachDocument(client, CautionDocumentType.SMS, dcm, dossier.id)
        attachDocument(client, CautionDocumentType.AFC, dcm, dossier.id)
        assertEquals(2, cautions.dossierDocuments(dossier.id).size)

        cautions.deleteDossier(dossier.id)

        assertNull(cautions.findDossier(dossier.id))
        assertTrue(cautions.dossierDocuments(dossier.id).isEmpty())
        assertFailsWith<ResponseStatusException> { cautions.deleteDossier(dossier.id) }
    }

    @Test
    fun `a finalized dossier cannot be deleted`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val dossier = cautions.createDossier(dossierCommand(client, emptyMap(), dcm))
        cautions.finalizeDossier(dossier.id, dcm)

        assertFailsWith<ResponseStatusException> { cautions.deleteDossier(dossier.id) }
    }

    @Test
    fun `finalize locks the dossier, proroge reopens it, refinalize re-locks and journals every step`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val dossier = cautions.createDossier(dossierCommand(client, emptyMap(), dcm))
        assertEquals(DossierStatus.BROUILLON, dossier.status)
        attachDocument(client, CautionDocumentType.SMS, dcm, dossier.id)

        // BROUILLON: amend allowed, business version bumps.
        val amended = cautions.updateDossier(dossier.id, mapOf("beneficiaire" to "EDG"))
        assertEquals(2, amended.version)

        // Finalize the request: the dossier locks and its documents are frozen.
        val finalized = cautions.finalizeDossier(dossier.id, dcm)
        assertEquals(DossierStatus.FINALISE, finalized.status)
        assertTrue(cautions.dossierDocuments(dossier.id).all { it.status == CautionStatus.FINAL })
        assertFailsWith<ResponseStatusException> { cautions.updateDossier(dossier.id, mapOf("x" to "y")) }

        // Proroge (manager): reopens for a targeted correction.
        val prorogated = cautions.prorogeDossier(dossier.id, dcm, "Report d'échéance par le maître d'ouvrage")
        assertEquals(DossierStatus.EN_PROROGATION, prorogated.status)
        cautions.updateDossier(dossier.id, mapOf("beneficiaire" to "EDG SA")) // writable again

        // Refinalize: re-locks and bumps the business version.
        val refinalized = cautions.refinalizeDossier(dossier.id, dcm)
        assertEquals(DossierStatus.FINALISE, refinalized.status)
        assertTrue(refinalized.version > finalized.version)

        val events = cautions.dossierEvents(dossier.id)
        assertEquals(
            setOf(DossierAction.FINALIZE, DossierAction.PROROGE, DossierAction.REFINALIZE),
            events.map { it.action }.toSet(),
        )
        assertTrue(events.any { it.reason == "Report d'échéance par le maître d'ouvrage" })
    }

    @Test
    fun `proroge requires a finalized dossier`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val dossier = cautions.createDossier(dossierCommand(client, emptyMap(), dcm))
        assertFailsWith<ResponseStatusException> { cautions.prorogeDossier(dossier.id, dcm, "motif") }
    }

    @Test
    fun `a document inherits its dossier's common fields at render, requiring only its specific fields`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        // The dossier carries the common context (bénéficiaire, montant, signataires…).
        val dossier = cautions.createDossier(dossierCommand(client, smsContent, dcm))
        // The document provides only its SPECIFIC fields (amount, currency, dates); the common ones are inherited.
        val document =
            cautions.create(
                CreateCautionCommand(
                    client,
                    CautionDocumentType.SMS,
                    mapOf("devise" to "GNF", "montant" to "306000000", "dateOffre" to "2026-02-13", "dateExpiration" to "2026-05-13"),
                    dcm,
                    nextSequence(CautionDocumentType.SMS),
                    dossierId = dossier.id,
                ),
            )
        cautions.finalizeDossier(dossier.id, dcm)

        val text = docText(export.export(document.id).content)

        assertContains(text, "MINISTERE DE L'ELEVAGE") // bénéficiaire inherited from the dossier
        assertContains(text, "306 000 000") // montant, the document's own specific field
        assertContains(text, "13 Mai 2026") // dateExpiration, the document's own specific field
    }

    @Test
    fun `editing a document records a version with before, after, reason and actor`() {
        val dcm = dcmMemberId()
        val client = clientId(dcm)
        val dossier = cautions.createDossier(dossierCommand(client, smsContent, dcm))
        val document =
            cautions.create(
                CreateCautionCommand(
                    client,
                    CautionDocumentType.SMS,
                    mapOf("devise" to "GNF", "montant" to "306000000", "dateOffre" to "2026-02-13", "dateExpiration" to "2026-05-13"),
                    dcm,
                    nextSequence(CautionDocumentType.SMS),
                    dossierId = dossier.id,
                ),
            )

        cautions.update(
            document.id,
            UpdateCautionCommand(
                mapOf("devise" to "GNF", "montant" to "306000000", "dateOffre" to "2026-02-14", "dateExpiration" to "2026-05-20"),
                reason = "Correction de la date",
            ),
            dcm,
        )

        val history = cautions.documentHistory(document.id)
        assertEquals(1, history.size)
        val version = history.first()
        assertEquals("2026-02-13", version.contentBefore["dateOffre"])
        assertEquals("2026-02-14", version.contentAfter["dateOffre"])
        assertEquals("Correction de la date", version.reason)
        assertEquals(dcm, version.actor)
    }

    @Test
    fun `exports the dossier notification with its sections and entered content`() {
        val dcm = dcmMemberId()
        val client =
            clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "SOCIETE GUINEE BATI BUSINESS SARL", dcm)).id
        val dossier =
            cautions.createDossier(
                CreateDossierCommand(
                    clientId = client,
                    sequence = nextDossierSequence(),
                    content =
                        mapOf(
                            "notifReference" to "N°/     /AFB/DCM/DGA/26",
                            "dateEmission" to "2026-07-21",
                            "destinataireNom" to "Mr Kalil Kourouma",
                            "vReference" to "0051/07/GBB/2026",
                            "demandeResume" to "trois (03) cautions de soumissions et trois (03) attestations de facilité de crédit",
                            "articulationConcours" to
                                "Trois (03) cautions de soumission de GNF 306 000 000 : Lot 4, Lot6 et LOT8 ;\n" +
                                "Trois (03) attestations de facilité de crédit de GNF 4 000 000 000 : LOT4, LOT6 et Lot8.",
                            "garantiesDetenues" to "RAS",
                            "garantiesARecueillir" to "Signature de trois (03) traites.",
                            "signataire1Nom" to "QUENTIN DETCHENOU",
                            "signataire1Titre" to "Directeur Crédit Marketing",
                            "signataire2Nom" to "FANNY SOUMAH",
                            "signataire2Titre" to "Directrice Générale Adjointe",
                        ),
                    createdBy = dcm,
                ),
            )

        val result = export.exportDossierNotification(dossier.id)
        val text =
            XWPFDocument(ByteArrayInputStream(result.content)).use { doc ->
                buildString {
                    doc.paragraphs.forEach { appendLine(it.text) }
                    doc.tables.forEach { t -> t.rows.forEach { r -> r.tableCells.forEach { appendLine(it.text) } } }
                }
            }

        assertTrue(result.filename.startsWith("notification-"))
        assertContains(text, "Objet : Notification de caution")
        assertContains(text, "SOCIETE GUINEE BATI BUSINESS SARL")
        assertContains(text, "Mr Kalil Kourouma")
        assertContains(text, "ARTICULATION DES CONCOURS :")
        assertContains(text, "Trois (03) cautions de soumission de GNF 306 000 000 : Lot 4, Lot6 et LOT8 ;")
        assertContains(text, "II. GARANTIES RETENUES :")
        assertContains(text, "Signature de trois (03) traites.")
        assertContains(text, "III. CONDITIONS DE BANQUE :")
        // The conditions are printed from the dossier's fee schedule, not from a
        // free-text field: the letter and the Fiche state the same rule by construction.
        assertContains(text, "Com. d'engagement : 1% Min GNF 1 000 000 ;")
        assertContains(text, "Frais de délivrance : 0,1% Min GNF 500 000 ;")
        assertContains(text, "QUENTIN DETCHENOU")
    }

    @Test
    fun `each lot takes the objet line at its own rank, and a single line covers them all`() {
        val dcm = dcmMemberId()
        val client = clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "SOCIETE GUINEE BATI BUSINESS SARL", dcm)).id
        val dossier =
            cautions.createDossier(
                CreateDossierCommand(
                    clientId = client,
                    sequence = nextDossierSequence(),
                    content =
                        mapOf(
                            "beneficiaire" to "MINISTERE DE L'ELEVAGE",
                            "referenceAppelOffres" to "N°01/MAGEL/DNAPA/PRMP/2026",
                            // One line per lot, in the same order as "lots".
                            "objetMarche" to
                                "Travaux de construction d'un marché à bétail a Kankan Lot4\n" +
                                "Travaux de construction d'un marché à bétail a N'zérékoré Lot8",
                            "dateEmission" to "2026-07-21",
                            "lots" to "Lot 4, Lot 8",
                            "signataire1Nom" to "QUENTIN DETCHENOU",
                            "signataire1Titre" to "Directeur Crédit Marketing",
                            "signataire2Nom" to "FANNY SOUMAH",
                            "signataire2Titre" to "Directrice Générale Adjointe",
                        ),
                    createdBy = dcm,
                ),
            )

        fun afcFor(lot: String) =
            cautions.create(
                CreateCautionCommand(
                    clientId = client,
                    documentType = CautionDocumentType.AFC,
                    dossierId = dossier.id,
                    content = mapOf("lot" to lot, "devise" to "GNF", "montant" to "4 000 000 000"),
                    createdBy = dcm,
                    sequence = cautions.suggestNextSequence(CautionDocumentType.AFC),
                ),
            )

        // Each document picks the objet line at its lot's rank, nothing retyped on it.
        val lot4Text = docText(export.export(afcFor("Lot 4").id).content)
        assertContains(lot4Text, "Travaux de construction d'un marché à bétail a Kankan Lot4")
        assertTrue(!lot4Text.contains("N'zérékoré"))

        val lot8Text = docText(export.export(afcFor("Lot 8").id).content)
        assertContains(lot8Text, "Travaux de construction d'un marché à bétail a N'zérékoré Lot8")
        assertTrue(!lot8Text.contains("Kankan"))

        // Section 3 lists them lot by lot when the wordings differ.
        val fiche = docText(export.exportDossierFiche(dossier.id).content)
        assertContains(fiche, "Lot 4 : Travaux de construction d'un marché à bétail a Kankan Lot4")
        assertContains(fiche, "Lot 8 : Travaux de construction d'un marché à bétail a N'zérékoré Lot8")
    }

    @Test
    fun `a single objet line covers every lot and section 3 enumerates them`() {
        val dcm = dcmMemberId()
        val client = clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "SOCIETE GUINEE BATI BUSINESS SARL", dcm)).id
        val dossier =
            cautions.createDossier(
                CreateDossierCommand(
                    clientId = client,
                    sequence = nextDossierSequence(),
                    content =
                        mapOf(
                            "beneficiaire" to "MINISTERE DE L'ELEVAGE",
                            "referenceAppelOffres" to "N°01/MAGEL/DNAPA/PRMP/2026",
                            "objetMarche" to "Travaux de construction de dix marchés à bétails en dix (10) lots distincts",
                            "dateEmission" to "2026-07-21",
                            "lots" to "Lot 4, Lot 6, Lot 8",
                            "signataire1Nom" to "QUENTIN DETCHENOU",
                            "signataire1Titre" to "Directeur Crédit Marketing",
                            "signataire2Nom" to "FANNY SOUMAH",
                            "signataire2Titre" to "Directrice Générale Adjointe",
                        ),
                    createdBy = dcm,
                ),
            )

        // Worded as the paper model: the objet, then the lots it covers.
        val fiche = docText(export.exportDossierFiche(dossier.id).content)
        assertContains(fiche, "Travaux de construction de dix marchés à bétails en dix (10) lots distincts : Lot 4, Lot 6 et Lot 8.")
    }

    @Test
    fun `exports the dossier fiche with its sections and computed totals`() {
        val dcm = dcmMemberId()
        val client =
            clients
                .create(
                    CreateClientCommand(
                        matricule = "M-${UUID.randomUUID()}",
                        raisonSociale = "SOCIETE GUINEE BATI BUSINESS SARL",
                        createdBy = dcm,
                        agence = "KALOUM",
                        gestionnaire = "DGA3",
                        dateEntreeRelation = LocalDate.of(2020, 2, 20),
                    ),
                ).id
        val dossier =
            cautions.createDossier(
                CreateDossierCommand(
                    clientId = client,
                    sequence = nextDossierSequence(),
                    content =
                        mapOf(
                            "beneficiaire" to "MINISTERE DE L'ELEVAGE",
                            "referenceAppelOffres" to "N°01/MAGEL/DNAPA/PRMP/2026",
                            "objetMarche" to "Travaux Lot 4, Lot6 et Lot8",
                            "numeroCompte" to "0101788201 05",
                            "mouvementConfie" to "1 136 805 909",
                            "solde" to "8 721 004",
                            "soldeDate" to "22/07/2025",
                            "engSoumissionEncours" to "987 828 828",
                            "engSoumissionSollicite" to "306 000 000",
                            "engTresorerieEncours" to "0",
                            "engTresorerieSollicite" to "0",
                            "lots" to "Lot 4, Lot 6, Lot 8",
                        ),
                    createdBy = dcm,
                ),
            )

        // Lot 4 carries a caution and an attestation; the Fiche's per-lot figures
        // must come from these two documents, with nothing keyed in on the dossier.
        cautions.create(
            CreateCautionCommand(
                clientId = client,
                documentType = CautionDocumentType.SMS,
                dossierId = dossier.id,
                content =
                    mapOf(
                        "lot" to "Lot 4",
                        "devise" to "GNF",
                        "montant" to "306 000 000",
                        "dateOffre" to "2026-07-13",
                        "dateExpiration" to "2026-10-13",
                    ),
                createdBy = dcm,
                sequence = cautions.suggestNextSequence(CautionDocumentType.SMS),
            ),
        )
        cautions.create(
            CreateCautionCommand(
                clientId = client,
                documentType = CautionDocumentType.AFC,
                dossierId = dossier.id,
                content = mapOf("lot" to "Lot 4", "devise" to "GNF", "montant" to "4 000 000 000"),
                createdBy = dcm,
                sequence = cautions.suggestNextSequence(CautionDocumentType.AFC),
            ),
        )

        val result = export.exportDossierFiche(dossier.id)
        val text = docText(result.content)

        assertContains(text, "FICHE D'APPROBATION DE CAUTION DE SOUMISSION")
        assertContains(text, "1- PRESENTATION DU CLIENT")
        assertContains(text, "SOCIETE GUINEE BATI BUSINESS SARL")
        assertContains(text, "DGA3")
        assertContains(text, "20/02/2020")

        // Section 4 is derived from the attached documents, never retyped.
        assertContains(text, "4- SOLLICITATIONS")
        assertContains(text, "Lot 4 : GNF 306 000 000")
        assertContains(text, "Lot 4 : GNF 4 000 000 000")

        // Section 6 reproduces the reference FICHE APPROBATION.docx line for line:
        // max(montant x taux, minimum) x (1 + tva), on the lot's own documents.
        assertContains(text, "6- CONDITIONS DE BANQUES ET RENTABILITE")
        assertContains(text, "MT TTC Lot 4")
        assertContains(text, "COM ENG = 1% Min GNF 1 000 000")
        assertContains(text, "3 457 800")
        assertContains(text, "3 610 800")
        assertContains(text, "565 000")
        assertContains(text, "4 720 000")
        assertContains(text, "12 353 600")
        assertContains(text, "7- APPROBATIONS")

        // The fiche is a one-sheet internal form: narrow margins and 9pt type are
        // what keep its seven sections on a single page (verified at 3 lots / 6
        // documents, the worst case the DCM produces).
        XWPFDocument(ByteArrayInputStream(result.content)).use { doc ->
            val margins = doc.document.body.sectPr.pgMar
            assertEquals(1134, margins.left.toString().toInt())
            assertEquals(1134, margins.right.toString().toInt())
            assertEquals(851, margins.top.toString().toInt())
            assertEquals(851, margins.bottom.toString().toInt())
            val runs =
                doc.tables
                    .flatMap { it.rows }
                    .flatMap { it.tableCells }
                    .flatMap { it.paragraphs }
                    .flatMap { it.runs }
            assertTrue(runs.isNotEmpty() && runs.all { it.fontSizeAsDouble == 9.0 })
        }
    }

    @Test
    fun `the notification clears the letterhead's pre-printed left band`() {
        val dcm = dcmMemberId()
        val client = clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "SOCIETE GUINEE BATI BUSINESS SARL", dcm)).id
        val dossier =
            cautions.createDossier(
                CreateDossierCommand(
                    clientId = client,
                    sequence = nextDossierSequence(),
                    content = mapOf("dateEmission" to "2026-07-21", "destinataireNom" to "Mr Kalil Kourouma"),
                    createdBy = dcm,
                ),
            )

        val result = export.exportDossierNotification(dossier.id)

        // Matches NOTIFICATION.docx's text block: its 1417 margin plus the 1418
        // indent every body paragraph carries, and the 624 it reclaims on the right.
        XWPFDocument(ByteArrayInputStream(result.content)).use { doc ->
            val margins = doc.document.body.sectPr.pgMar
            assertEquals(2835, margins.left.toString().toInt())
            assertEquals(793, margins.right.toString().toInt())
        }
    }
}
