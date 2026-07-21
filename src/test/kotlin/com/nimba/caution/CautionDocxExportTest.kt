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
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.util.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CautionDocxExportTest(
    @Autowired private val cautions: CautionModuleApi,
    @Autowired private val clients: ClientModuleApi,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val export: CautionDocxExportService,
) {
    private fun dcmMemberId(): UUID =
        requireNotNull(seedMember(users, passwordEncoder, "caution-export-${UUID.randomUUID()}@banque.test", Department.DCM).id)

    private fun signatoryFields(): Map<String, String> =
        mapOf(
            "signataire1Nom" to "QUENTIN DETCHENOU",
            "signataire1Titre" to "Directeur Crédit Marketing",
            "signataire2Nom" to "FANNY SOUMAH",
            "signataire2Titre" to "Directrice Générale Adjointe",
        )

    private fun allText(bytes: ByteArray): String =
        XWPFDocument(ByteArrayInputStream(bytes)).use { doc ->
            buildString {
                doc.paragraphs.forEach { appendLine(it.text) }
                doc.tables.forEach { table -> table.rows.forEach { row -> row.tableCells.forEach { appendLine(it.text) } } }
            }
        }

    @Test
    fun `exports a finalized SMS as the replica structure`() {
        val dcm = dcmMemberId()
        val client =
            clients.create(
                CreateClientCommand(
                    matricule = "038044-${UUID.randomUUID()}",
                    raisonSociale = "GUINEENNE DES TRAVAUX ET FOURNITURES - SARLU",
                    createdBy = dcm,
                    agence = "PRINCIPALE",
                ),
            )
        val created =
            cautions.create(
                CreateCautionCommand(
                    clientId = client.id,
                    documentType = CautionDocumentType.SMS,
                    content =
                        signatoryFields() +
                            mapOf(
                                "beneficiaire" to "ELECTRICITE DE GUINEE EDG-SA",
                                "referenceAppelOffres" to "AAONO N°: 001/EDG-SA/DAAL/PRMP/2026",
                                "objetMarche" to "Travaux de Réfection des Bâtiments du site de Garafiri (EDG-SA)",
                                "devise" to "GNF",
                                "montant" to "238756476",
                                "dateEmission" to "2026-02-11",
                                "dateOffre" to "2026-02-13",
                                "dateExpiration" to "2026-05-13",
                            ),
                    createdBy = dcm,
                ),
            )
        cautions.finalize(created.id)

        val result = export.export(created.id)
        val text = allText(result.content)

        assertTrue(result.filename.startsWith("caution-"))
        assertContains(text, "CAUTION DE SOUMISSION")
        assertContains(text, created.referenceNumber)
        assertContains(text, "GUINEENNE DES TRAVAUX ET FOURNITURES - SARLU")
        assertContains(text, "ELECTRICITE DE GUINEE EDG-SA")
        assertContains(text, "238 756 476")
        assertContains(text, "Deux Cent Trente Huit Millions Sept Cent Cinquante Six Mille Quatre Cent Soixante Seize Francs Guinéens")
        assertContains(text, "National Restreint")
        assertContains(text, "QUENTIN DETCHENOU")
        assertContains(text, "Directeur Crédit Marketing")
        assertContains(text, "FANNY SOUMAH")
        assertTrue(!text.contains("Monsieur"))
        assertTrue(!text.contains("Madame"))

        val doc = XWPFDocument(ByteArrayInputStream(result.content))
        val fonts =
            doc.paragraphs
                .flatMap { it.runs }
                .mapNotNull { it.fontFamily }
                .toSet()
        assertEquals(setOf("Tahoma"), fonts)
        doc.close()
    }

    @Test
    fun `exports a finalized ACF as the replica structure`() {
        val dcm = dcmMemberId()
        val client =
            clients.create(
                CreateClientCommand(
                    matricule = "038044-${UUID.randomUUID()}",
                    raisonSociale = "GUINEENNE DES TRAVAUX ET FOURNITURES - SARLU",
                    createdBy = dcm,
                    sigle = "G-TRAF +",
                    rccm = "GN.2025.B.07118",
                    accountNumber = "021 001 0103804401 34",
                    agence = "Kaloum",
                ),
            )
        val created =
            cautions.create(
                CreateCautionCommand(
                    clientId = client.id,
                    documentType = CautionDocumentType.ACF,
                    content =
                        signatoryFields() +
                            mapOf(
                                "beneficiaire" to "L'ELECTRICITE DE GUINEE EDG SA",
                                "referenceAppelOffres" to "007/EDG-SA/DAAL/DPSM/2025",
                                "objetMarche" to "Travaux de réfection des bâtiments de GARAFIRI (EDG-SA).LOT1",
                                "devise" to "GNF",
                                "montant" to "2828096140",
                                "dateEmission" to "2026-02-19",
                            ),
                    createdBy = dcm,
                ),
            )
        cautions.finalize(created.id)

        val result = export.export(created.id)
        val text = allText(result.content)

        assertContains(text, "ATTESTATION DE CAPACITE FINANCIERE")
        assertContains(text, "G-TRAF +")
        assertContains(text, "GN.2025.B.07118")
        assertContains(text, "021 001 0103804401 34")
        assertContains(text, "Kaloum")
        assertContains(text, "2 828 096 140")
        assertContains(text, "Deux Milliards Huit Cent Vingt Huit Millions Quatre Vingt Seize Mille Cent Quarante")
        assertContains(text, "19 Février 2026")
        assertTrue(!text.contains("Monsieur"))
        assertTrue(!text.contains("Madame"))
    }

    @Test
    fun `a draft caution cannot be exported`() {
        val dcm = dcmMemberId()
        val client = clients.create(CreateClientCommand("M-${UUID.randomUUID()}", "Client test", dcm))
        val created =
            cautions.create(
                CreateCautionCommand(
                    clientId = client.id,
                    documentType = CautionDocumentType.ACF,
                    content =
                        signatoryFields() +
                            mapOf(
                                "beneficiaire" to "X",
                                "referenceAppelOffres" to "X",
                                "objetMarche" to "X",
                                "devise" to "GNF",
                                "montant" to "1000",
                                "dateEmission" to "2026-01-01",
                            ),
                    createdBy = dcm,
                ),
            )

        assertFailsWith<ResponseStatusException> { export.export(created.id) }
    }
}
