package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionFieldRegistry
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionModuleApi
import com.nimba.client.ClientInfo
import com.nimba.client.ClientModuleApi
import com.nimba.client.getOrThrow
import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.OrganizationLogo
import com.nimba.shared.amountInWords
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.TableRowAlign
import org.apache.poi.xwpf.usermodel.TableWidthType
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO

/** One exported caution document, ready to stream back as a download. */
data class CautionExport(
    val filename: String,
    val content: ByteArray,
)

/** One run of text within a paragraph, bold or not — every value the DCM entered via the creation form is printed bold, exactly like the bank marks up its own paper templates. */
private data class Segment(
    val text: String,
    val bold: Boolean = false,
)

private fun plain(text: String) = Segment(text)

private fun bold(text: String) = Segment(text, bold = true)

/**
 * Builds the Word (.docx) export of a caution as an exact replica of the
 * bank's real documents (ground truth: `CAUTION.docx` and `ATTESTATION DE
 * CAPACITE FINANCIERE.docx` in docs/caution): A4, the templates' margins,
 * Tahoma justified body, the double-bordered/shaded header box, and every
 * bold run matching the reference exactly. A FINAL caution prints its frozen
 * client snapshot; a DRAFT is exportable too, as a preview, rendered from the
 * live client record so the DCM can check the document before finalizing.
 * Signatories are the caution's own content fields (not a bank-wide setting):
 * a signatory can differ from one document to the next (delegation), so each
 * document keeps its own answer once finalized, same as every other entered
 * field.
 */
@Service
class CautionDocxExportService(
    private val cautions: CautionModuleApi,
    private val clients: ClientModuleApi,
    private val identity: IdentityModuleApi,
) {
    private val shortDate = DateTimeFormatter.ofPattern("dd-MM-uu")

    @Transactional(readOnly = true)
    fun export(id: UUID): CautionExport {
        val stored =
            cautions.findById(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable")
        // Resolve the document's effective content: it inherits its dossier's common fields and overrides with its own.
        val caution = resolveEffective(stored)
        // A finalized caution carries its frozen snapshot; a draft preview reads the client live.
        val snapshot = caution.clientSnapshot ?: liveSnapshot(caution.clientId)

        val document = XWPFDocument()
        setUpPage(document)
        when (caution.documentType) {
            CautionDocumentType.SMS -> renderSms(document, caution, snapshot.raisonSociale.orRas(), snapshot.agence)
            CautionDocumentType.ACF -> renderAcf(document, caution, snapshot)
            CautionDocumentType.AFC -> renderAfc(document, caution, snapshot)
            CautionDocumentType.PRO -> renderProrogation(document, caution)
        }

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        val suffix = if (caution.clientSnapshot == null) "-brouillon" else ""
        return CautionExport("caution-${caution.referenceNumber}$suffix.docx", bytes)
    }

    /**
     * A document's effective content for rendering: the dossier's common fields
     * inherited, then overridden by the document's own answers (see
     * [CautionFieldRegistry.effectiveContent]). A legacy standalone document, or
     * one whose dossier is gone, renders from its own content as before.
     */
    private fun resolveEffective(caution: CautionInfo): CautionInfo {
        val dossier = caution.dossierId?.let { cautions.findDossier(it) } ?: return caution
        val commonKeys = CautionFieldRegistry.commonFields().map { it.key }.toSet()
        val common = dossier.content.filterKeys { it in commonKeys }
        return caution.copy(content = CautionFieldRegistry.effectiveContent(common, caution.content))
    }

    /** The live client record projected onto the same shape as the frozen snapshot, for draft previews. */
    private fun liveSnapshot(clientId: UUID): CautionClientSnapshotInfo {
        val client = clients.getOrThrow(clientId)
        return CautionClientSnapshotInfo(
            matricule = client.matricule,
            raisonSociale = client.raisonSociale,
            sigle = client.sigle,
            adressePhysique = client.adressePhysique,
            rccm = client.rccm,
            accountNumber = client.accountNumber,
            agence = client.agence,
        )
    }

    /**
     * The dossier's Notification de caution — a companion letter summarizing the
     * whole request (articulation des concours, garanties, conditions de banque),
     * rendered from the dossier's own content and the live client record.
     */
    @Transactional(readOnly = true)
    fun exportDossierNotification(dossierId: UUID): CautionExport {
        val dossier =
            cautions.findDossier(dossierId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")
        val snapshot = liveSnapshot(dossier.clientId)

        val document = XWPFDocument()
        setUpPage(document)
        renderNotification(document, dossier.content, snapshot)

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return CautionExport("notification-${dossier.referenceNumber}-v${dossier.version}.docx", bytes)
    }

    /**
     * The dossier's internal Fiche d'approbation de caution de soumission — the
     * organisation logo, then the seven sections (client, documents, marché,
     * sollicitations, engagements, conditions/rentabilité, approbations),
     * rendered from the live client record and the figures entered on the
     * dossier, with the totals computed.
     */
    @Transactional(readOnly = true)
    fun exportDossierFiche(dossierId: UUID): CautionExport {
        val dossier =
            cautions.findDossier(dossierId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")
        val client = clients.getOrThrow(dossier.clientId)

        val document = XWPFDocument()
        setUpPage(document)
        renderFiche(document, dossier.content, client, identity.organizationLogo())

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return CautionExport("fiche-approbation-${dossier.referenceNumber}-v${dossier.version}.docx", bytes)
    }

    // ---- Caution de Soumission (SMS) ---------------------------------------------------

    private fun renderSms(
        document: XWPFDocument,
        caution: CautionInfo,
        raisonSociale: String,
        agence: String?,
    ) {
        val c = caution.content
        headerBox(document, "CAUTION DE SOUMISSION", caution.referenceNumber)
        spacer(document)

        boldCenteredLine(document, "AFRILAND FIRST BANK ; AGENCE ${agence.orRas()}")
        boldCenteredLine(document, "BENEFICIAIRE : ${c["beneficiaire"].orRas()}")
        spacer(document)
        boldCenteredLine(document, "DATE : ${fmtShort(c["dateEmission"])}")
        boldCenteredLine(document, "GARANTIE N° ${caution.referenceNumber}")
        spacer(document)

        mixedParagraph(
            document,
            plain("Nous avons été informés que la société "),
            bold(raisonSociale),
            plain(" (Ci-après dénommée "),
            bold("« le Candidat »"),
            plain(") a répondu à votre appel d'offres National Restreint "),
            bold(c["referenceAppelOffres"].orRas()),
            plain(" relatif aux : "),
            bold(c["objetMarche"].orRas()),
            plain(" Et vous a soumis son offre en date du "),
            bold(fmtLong(c["dateOffre"])),
            plain(" (ci-après dénommée « "),
            bold("l'offre"),
            plain(" »)."),
        )
        paragraph(
            document,
            "En vertu des dispositions du dossier d'Appel d'offres, l'Offre doit être accompagnée d'une garantie d'offre.",
        )
        mixedParagraph(
            document,
            plain("A la demande du Maître d'ouvrage, nous "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya-Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                    "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC–KAL/040.445A/2012 du " +
                    "17 Mai 2012, représentée par ",
            ),
            bold(signatoryLabel(c, 1)),
            plain(" et "),
            bold(signatoryLabel(c, 2)),
            plain(" dûment habilités, ci-après dénommée "),
            bold("« la Banque »"),
            plain(" ;"),
        )
        mixedParagraph(
            document,
            plain(
                "Nous engageons par la présente, sans réserve et irrévocablement, à vous payer à première demande, " +
                    "toute somme d'argent que vous pourriez réclamer dans la limite de ",
            ),
            bold("${amountClause(c)}."),
        )
        paragraph(
            document,
            "Votre demande en paiement doit être accompagnée d'une déclaration attestant que le Soumissionnaire n'a " +
                "pas exécuté une des obligations auxquelles il est tenu en vertu de l'Offre à savoir :",
        )
        listItem(
            document,
            "a)",
            "S'il retire l'Offre pendant la période de validité qu'il a spécifiée dans la lettre de soumission de " +
                "l'offre ; ou pendant toute prolongation de la période de validité de l'offre qu'il aura effectuée ; ou",
        )
        listItem(
            document,
            "b)",
            "si, s'étant vu notifier l'acceptation de l'Offre par le maître de l'ouvrage pendant la période de " +
                "validité telle qu'indiquée dans la lettre de soumission de l'offre ou prorogée par le maître de " +
                "l'ouvrage avant l'expiration de cette période, il (i) ne signe pas l'acte d'engagement du Marché ; " +
                "ou (ii) il ne fournit pas la garantie de bonne réalisation du Marché et, s'il est tenu de le faire " +
                "ne fournit pas la garantie de performance environnementale, sociale, hygiène et sécurité (ESHS), " +
                "ainsi qu'il est prévu dans les instructions aux soumissionnaires.",
        )
        paragraph(
            document,
            "La présente garantie expire (a) si le marché est octroyé au Soumissionnaire, lorsque nous recevrons une " +
                "copie du Marché signé et de la garantie de bonne exécution émise, et si cela est exigé, la garantie " +
                "de performance environnementale, sociale, hygiène et sécurité (ESHS) émise en votre nom, selon les " +
                "instructions du Soumissionnaire ; ou (b) si le marché n'est pas octroyé au Soumissionnaire, à la " +
                "première des dates suivantes (i) vingt-huit (28) après l'expiration de l'offre ou (c) trois ans " +
                "après la date d'émission de la présente garantie.",
        )
        mixedParagraph(
            document,
            plain("Toute demande de paiement au titre de la présente garantie doit être reçue à cette date au plus tard le "),
            bold("${fmtLong(c["dateExpiration"])}."),
        )
        paragraph(
            document,
            "La présente garantie est régie par les règles uniformes de la chambre de commerce Internationale (CCI) " +
                "relatives aux garanties sur demande, Publication CCI N°758.",
        )
        spacer(document)
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    // ---- Attestation de Capacité Financière (ACF) --------------------------------------

    /**
     * Renders the Attestation de Capacité Financière as a faithful replica of
     * `ATTESTATION DE CAPACITE FINANCIERE.docx`: a page-centered header box at
     * the model's width, the two reference lines fully bold, and the body with
     * exactly the model's bold runs — the company name, its acronym and its
     * account number are bold; its address, RCCM and agency are not (matching
     * the reference). The amount is bold as a whole. Civility prefixes
     * (Monsieur/Madame) are intentionally dropped, as on the caution, so an
     * unset signatory never prints a gendered title.
     */
    private fun renderAcf(
        document: XWPFDocument,
        caution: CautionInfo,
        snapshot: CautionClientSnapshotInfo,
    ) {
        val c = caution.content
        headerBox(
            document,
            "ATTESTATION DE CAPACITE FINANCIERE",
            caution.referenceNumber,
            widthDxa = ACF_HEADER_WIDTH,
            centered = true,
        )
        spacer(document)
        spacer(document)

        mixedParagraph(
            document,
            plain("Adressée à "),
            bold(c["beneficiaire"].orRas()),
            alignment = ParagraphAlignment.CENTER,
        )
        spacer(document)
        mixedParagraph(document, bold("N/Référence : N°${caution.referenceNumber}"), alignment = ParagraphAlignment.LEFT)
        mixedParagraph(document, bold("V/Référence : ${c["referenceAppelOffres"].orRas()}"))
        mixedParagraph(document, bold(c["objetMarche"].orRas()))
        spacer(document)

        mixedParagraph(
            document,
            plain("Nous soussignés, "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya - Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                    "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC – KAL/040.445A/2012 du " +
                    "17 Mai 2012, représentée par ",
            ),
            bold(signatoryLabel(c, 1)),
            plain(" et "),
            bold(signatoryLabel(c, 2)),
            plain(", en vertu des pouvoirs dont ils sont investis."),
        )

        val certifySegments =
            buildList {
                add(plain("Certifions par la présente que la société "))
                add(bold(snapshot.raisonSociale.orRas()))
                snapshot.sigle?.takeIf { it.isNotBlank() }?.let {
                    add(plain(" en abrégé « "))
                    add(bold(it))
                    add(plain(" »"))
                }
                add(
                    plain(
                        " siège social ${snapshot.adressePhysique.orRas()}, enregistrée au RCCM/${snapshot.rccm.orRas()} " +
                            "est titulaire du compte N°",
                    ),
                )
                add(bold(snapshot.accountNumber.orRas()))
                add(plain(" ouvert dans nos livres à l'Agence ${snapshot.agence.orRas()}."))
            }
        mixedParagraph(document, *certifySegments.toTypedArray())

        mixedParagraph(
            document,
            plain("L'Entreprise dispose à notre connaissance les moyens financiers de "),
            bold(amountClause(c)),
            plain(" nécessaires à la réalisation du marché pour lequel elle présente une offre."),
        )
        spacer(document)
        paragraph(document, "Fait pour servir et valoir ce que de droit.")
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    // ---- Attestation de Facilité de Crédit (AFC) ---------------------------------------

    /**
     * Renders the Attestation de Facilité de Crédit as a faithful replica of
     * `AFC  LOT8.docx`: a page-centered header box at the model's width, a
     * centered "Adressée à …" line, and the two credit clauses with the model's
     * bold runs — the bank certifies it would grant credit up to the amount, to
     * the company, for the market. The amount prints without the leading
     * currency code (the words carry the currency), matching the model.
     */
    private fun renderAfc(
        document: XWPFDocument,
        caution: CautionInfo,
        snapshot: CautionClientSnapshotInfo,
    ) {
        val c = caution.content
        headerBox(document, "ATTESTATION DE FACILITE DE CREDIT", caution.referenceNumber, widthDxa = AFC_HEADER_WIDTH, centered = true)
        spacer(document)
        spacer(document)

        mixedParagraph(
            document,
            plain("Adressée à "),
            bold(c["beneficiaire"].orRas()),
            alignment = ParagraphAlignment.CENTER,
        )
        spacer(document)

        mixedParagraph(
            document,
            plain("Nous soussignés, "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya - Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "inscrite sur la liste des banques et établissements financiers sous le numéro 021 et immatriculée " +
                    "au Registre de Commerce et du Crédit Mobilier de Conakry sous le numéro GC – KAL/040.445A/2012 du " +
                    "17 Mai 2012, représentée par ",
            ),
            bold(signatoryLabel(c, 1)),
            plain(" et "),
            bold(signatoryLabel(c, 2)),
            plain(" dûment habilités."),
        )
        mixedParagraph(
            document,
            plain("Attestons par la présente que nous serions disposés à consentir nos concours à hauteur de "),
            bold(amountClause(c, withCurrencyCode = false)),
            plain(" à la "),
            bold(snapshot.raisonSociale.orRas()),
            plain(" dans le cadre du Marché de l'appel d'offres National "),
            bold(c["referenceAppelOffres"].orRas()),
            plain(" relatif aux "),
            bold(c["objetMarche"].orRas()),
            plain("."),
        )
        mixedParagraph(
            document,
            plain("Cette attestation est délivrée à la "),
            bold(snapshot.raisonSociale.orRas()),
            plain(", siège social ${snapshot.adressePhysique.orRas()}, immatriculée sous le "),
            bold("N°RCCM/${snapshot.rccm.orRas()}"),
            plain(" pour servir et faire valoir ce que de droit."),
        )
        paragraph(
            document,
            "Ledit accompagnement se fera sous réserve de la validation par notre comité de Crédit Compétent, seul " +
                "organe habilité à statuer en matière de crédit dans notre institution.",
        )
        paragraph(document, "En foi de quoi, la présente certification est établie pour servir et faire valoir ce que de droit.")
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    // ---- Avenant de Prorogation (PRO) --------------------------------------------------

    /**
     * Renders an Avenant de Prorogation: a short deed that extends a finalized
     * caution's validity. It references the original caution (which stays
     * immutable) by its number and issue date, restates the guarantee, and sets
     * the new expiry date. There is no reference model for this document, so the
     * layout follows the same typography and header style as the caution.
     */
    private fun renderProrogation(
        document: XWPFDocument,
        caution: CautionInfo,
    ) {
        val c = caution.content
        headerBox(document, "AVENANT DE PROROGATION", caution.referenceNumber)
        spacer(document)

        mixedParagraph(
            document,
            plain("Nous, "),
            bold("Afriland First Bank Guinée S.A."),
            plain(", Société Anonyme au Capital de "),
            bold("GNF 200 000 000 000"),
            plain(
                ", dont le Siège Social est à Almamya - Commune de Kaloum, B.P. : 343, Conakry - République de Guinée, " +
                    "représentée par ",
            ),
            bold(signatoryLabel(c, 1)),
            plain(" et "),
            bold(signatoryLabel(c, 2)),
            plain(","),
        )
        mixedParagraph(
            document,
            plain("Faisant suite à la caution de soumission N° "),
            bold(c["cautionOrigineReference"].orRas()),
            plain(" émise le "),
            bold(fmtLong(c["cautionOrigineDate"])),
            plain(" en faveur de "),
            bold(c["beneficiaire"].orRas()),
            plain(", dans le cadre de l'appel d'offres "),
            bold(c["referenceAppelOffres"].orRas()),
            plain(" relatif aux "),
            bold(c["objetMarche"].orRas()),
            plain(", portant sur un montant de "),
            bold(amountClause(c)),
            plain(","),
        )
        mixedParagraph(
            document,
            plain("Prorogeons par la présente la validité de ladite caution jusqu'au "),
            bold("${fmtLong(c["nouvelleDateExpiration"])}."),
            plain(" Toutes les autres clauses et conditions de la caution d'origine demeurent inchangées."),
        )
        paragraph(
            document,
            "La présente garantie est régie par les règles uniformes de la chambre de commerce Internationale (CCI) " +
                "relatives aux garanties sur demande, Publication CCI N°758.",
        )
        spacer(document)
        val faitLe = document.createParagraph()
        faitLe.alignment = ParagraphAlignment.RIGHT
        addRun(faitLe, "Fait à Conakry, le ${fmtLong(c["dateEmission"])}", bold = true)
        spacer(document)
        renderSignatureBlock(document, c)
    }

    // ---- Notification de caution (dossier companion) -----------------------------------

    /**
     * Renders the Notification de caution as a replica of `NOTIFICATION.docx`: an
     * outgoing letter with the reference/date line, the right-aligned recipient
     * block, then the three numbered sections (articulation des concours,
     * garanties retenues, conditions de banque). The variable, dossier-specific
     * content (the demande summary, the articulation/garanties/conditions lines)
     * is entered on the dossier and printed line by line; section headings and
     * the fixed prose match the model.
     */
    private fun renderNotification(
        document: XWPFDocument,
        content: Map<String, String>,
        snapshot: CautionClientSnapshotInfo,
    ) {
        val refDate = document.createTable(1, 2)
        refDate.setWidthType(TableWidthType.DXA)
        refDate.setWidth(CONTENT_WIDTH)
        borderless(refDate)
        val half = (CONTENT_WIDTH / 2).toString()
        setCell(refDate.getRow(0).getCell(0), content["notifReference"].orRas(), alignment = ParagraphAlignment.LEFT, width = half)
        setCell(
            refDate.getRow(0).getCell(1),
            "Conakry, le ${fmtLong(content["dateEmission"])}",
            alignment = ParagraphAlignment.RIGHT,
            width = half,
        )
        spacer(document)

        rightBoldLine(document, snapshot.raisonSociale.orRas())
        rightBoldLine(document, "A l'Attention du ${content["destinataireFonction"] ?: "Directeur Général"}")
        rightBoldLine(document, content["destinataireNom"].orRas())
        spacer(document)

        mixedParagraph(document, bold("Objet : ${content["objet"] ?: "Notification de caution"}"), alignment = ParagraphAlignment.LEFT)
        mixedParagraph(document, bold("V/Réf : ${content["vReference"].orRas()}"), alignment = ParagraphAlignment.LEFT)
        spacer(document)

        paragraph(document, "Monsieur,")
        paragraph(
            document,
            "Votre correspondance ci-dessus relative à la demande de ${content["demandeResume"].orRas()} dans notre " +
                "institution financière a retenu toute notre attention et nous vous en remercions.",
        )
        paragraph(
            document,
            "Y faisant suite, nous avons le plaisir de vous confirmer que notre comité de crédit compétent pour votre " +
                "dossier a marqué son accord pour votre concours aux conditions suivantes :",
        )

        boldHeading(document, "ARTICULATION DES CONCOURS :")
        multilineParagraphs(document, content["articulationConcours"])

        boldHeading(document, "II. GARANTIES RETENUES :")
        mixedParagraph(document, bold("Garanties détenues : "), plain(content["garantiesDetenues"] ?: "RAS"))
        mixedParagraph(document, bold("Garanties à recueillir : "))
        multilineParagraphs(document, content["garantiesARecueillir"])

        boldHeading(document, "III. CONDITIONS DE BANQUE :")
        content["conditionsBanque"]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { labelledLine(document, it) }

        paragraph(
            document,
            "Pour la bonne règle, nous vous remercions de bien vouloir accuser réception de la présente, en nous " +
                "retournant la copie ci-jointe, dûment revêtue de votre signature, et précédée de la mention " +
                "« lu et approuvé, bon pour toutes les clauses ci-dessus ».",
        )
        paragraph(document, "Nous restons à votre entière disposition pour toutes informations complémentaires.")
        paragraph(
            document,
            "Espérant avoir répondu à vos attentes, nous réitérons nos remerciements pour l'intérêt porté à notre " +
                "institution et vous prions d'agréer, Monsieur, l'expression de nos salutations distinguées.",
        )
        spacer(document)
        renderSignatureBlock(document, content)
    }

    /** A right-aligned bold line, used for the recipient block of the notification. */
    private fun rightBoldLine(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.RIGHT
        addRun(p, text, bold = true)
    }

    /** A bold, left-aligned section heading (e.g. "ARTICULATION DES CONCOURS :"). */
    private fun boldHeading(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingBefore = 120
        p.spacingAfter = 80
        addRun(p, text, bold = true)
    }

    /** Splits an entered multi-line value into one justified paragraph per non-empty line. */
    private fun multilineParagraphs(
        document: XWPFDocument,
        value: String?,
    ) {
        value
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach { paragraph(document, it) }
    }

    /** A line whose label (up to and including the first colon) is bold, the rest plain — the notification's condition lines. */
    private fun labelledLine(
        document: XWPFDocument,
        line: String,
    ) {
        val colon = line.indexOf(':')
        if (colon >= 0) {
            mixedParagraph(document, bold(line.substring(0, colon + 1)), plain(line.substring(colon + 1)))
        } else {
            paragraph(document, line)
        }
    }

    // ---- Fiche d'approbation (dossier companion) ---------------------------------------

    /** One cell of a Fiche table. */
    private data class FCell(
        val text: String,
        val bold: Boolean = false,
        val alignment: ParagraphAlignment = ParagraphAlignment.LEFT,
    )

    private fun renderFiche(
        document: XWPFDocument,
        content: Map<String, String>,
        client: ClientInfo,
        logo: OrganizationLogo?,
    ) {
        val logoParagraph = document.createParagraph()
        logoParagraph.alignment = ParagraphAlignment.CENTER
        logo?.let { renderLogo(logoParagraph, it) }
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        addRun(title, "FICHE D'APPROBATION DE CAUTION DE SOUMISSION", bold = true, size = TITLE_SIZE)
        spacer(document)

        val dateEntree = client.dateEntreeRelation?.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))

        ficheSection(document, "1- PRESENTATION DU CLIENT")
        ficheTable(
            document,
            listOf(3600, CONTENT_WIDTH - 3600),
            listOf(
                listOf(FCell("CLIENT", bold = true), FCell(client.raisonSociale)),
                listOf(FCell("COMPTE", bold = true), FCell(client.accountNumber.orRas())),
                listOf(FCell("AGENCE", bold = true), FCell(client.agence.orRas())),
                listOf(FCell("GESTIONNAIRE", bold = true), FCell(client.gestionnaire.orRas())),
                listOf(FCell("DATE ENTREE EN RELATION", bold = true), FCell(dateEntree.orRas())),
                listOf(FCell("MOUVEMENT CONFIE", bold = true), FCell(content["mouvementConfie"].orRas())),
                listOf(FCell("SOLDE AU ${content["soldeDate"].orRas()}", bold = true), FCell(content["solde"].orRas())),
            ),
        )

        ficheSection(document, "2- DOCUMENTS A FOURNIR")
        ficheTable(
            document,
            thirds(),
            listOf(
                headerCells(listOf("DESIGNATIONS", "OUI", "NON")),
                docRow("DEMANDE", content["docDemande"]),
                docRow("DAO", content["docDao"]),
                docRow("COUVERTURE DES FRAIS", content["docCouvertureFrais"]),
                docRow("AUTRES", content["docAutres"]),
            ),
        )

        ficheSection(document, "3- DESCRIPTION DU MARCHE")
        ficheTable(
            document,
            thirds(),
            listOf(
                headerCells(listOf("N° D'APPEL D'OFFRE", "MAITRE D'OUVRAGE", "OBJET")),
                listOf(
                    FCell(content["referenceAppelOffres"].orRas()),
                    FCell(content["beneficiaire"].orRas()),
                    FCell(content["objetMarche"].orRas()),
                ),
            ),
        )

        ficheSection(document, "4- SOLLICITATIONS")
        ficheTable(
            document,
            listOf(3600, CONTENT_WIDTH - 3600),
            listOf(
                headerCells(listOf("DESIGNATIONS", "MONTANT")),
                listOf(FCell("CAUTION", bold = true), FCell(content["sollicitationCaution"].orRas())),
                listOf(FCell("PROMESSE DE FACILITE", bold = true), FCell(content["sollicitationPromesse"].orRas())),
            ),
        )

        ficheSection(document, "5- ENGAGEMENTS DANS NOS LIVRES")
        val trEnc = content["engTresorerieEncours"]
        val trSol = content["engTresorerieSollicite"]
        val soEnc = content["engSoumissionEncours"]
        val soSol = content["engSoumissionSollicite"]
        ficheTable(
            document,
            thirds(),
            listOf(
                headerCells(listOf("TYPES D'ENGAGEMENTS", "ENCOURS", "SOLLICITE")),
                listOf(FCell("ENG. PAR TRESORERIE"), FCell(trEnc.orRas()), FCell(trSol.orRas())),
                listOf(FCell("SOUMISSION"), FCell(soEnc.orRas()), FCell(soSol.orRas())),
                listOf(
                    FCell("TOTAL", bold = true),
                    FCell(sumGrouped(listOf(trEnc, soEnc)), bold = true),
                    FCell(sumGrouped(listOf(trSol, soSol)), bold = true),
                ),
            ),
        )

        ficheSection(document, "6- CONDITIONS DE BANQUES ET RENTABILITE")
        renderFicheConditions(document, content)

        ficheSection(document, "7- APPROBATIONS")
        val approvers = listOf("AE", "DCM", "DRC", "DER", "EXCO")
        val approvColumn = CONTENT_WIDTH / approvers.size
        ficheTable(
            document,
            List(approvers.size) { approvColumn },
            listOf(headerCells(approvers), List(approvers.size) { FCell(" ") }),
        )
    }

    /**
     * Section 6's conditions/rentabilité table, one column per lot. The per-lot,
     * per-condition amounts are entered on the dossier (the bank's fee formulas
     * are not derivable from the samples); the TOTAL row is the computed column
     * sum.
     */
    private fun renderFicheConditions(
        document: XWPFDocument,
        content: Map<String, String>,
    ) {
        val lots =
            content["lots"]
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.isNotEmpty() }
                ?: listOf("Montant")
        val conditionLabels = listOf("COM ENG", "F. CAUTION", "F. DELIVRANCE", "F. ATTESTATION")
        val labelColumn = 3400
        val lotColumn = (CONTENT_WIDTH - labelColumn) / lots.size
        val widths = listOf(labelColumn) + List(lots.size) { lotColumn }

        val rows = mutableListOf<List<FCell>>()
        rows.add(headerCells(listOf("CONDITIONS DE BANQUE") + lots.map { "MT TTC $it" }))
        conditionLabels.forEachIndexed { r, label ->
            rows.add(listOf(FCell(label)) + lots.indices.map { l -> FCell(content["cond_${r}_$l"].orRas()) })
        }
        val totals =
            listOf(FCell("TOTAL", bold = true)) +
                lots.indices.map { l ->
                    FCell(sumGrouped(conditionLabels.indices.map { r -> content["cond_${r}_$l"] }), bold = true)
                }
        rows.add(totals)
        ficheTable(document, widths, rows)
    }

    private fun ficheSection(
        document: XWPFDocument,
        title: String,
    ) {
        val p = document.createParagraph()
        p.spacingBefore = 160
        p.spacingAfter = 60
        addRun(p, title, bold = true)
    }

    /** Builds a thin-bordered table with fixed column widths (DXA) from rows of styled cells. */
    private fun ficheTable(
        document: XWPFDocument,
        widths: List<Int>,
        rows: List<List<FCell>>,
    ) {
        val table = document.createTable(rows.size, widths.size)
        table.setWidthType(TableWidthType.DXA)
        table.setWidth(widths.sum())
        thinBorders(table)
        rows.forEachIndexed { r, cells ->
            val row = table.getRow(r)
            cells.forEachIndexed { c, cell ->
                val tc = row.getCell(c)
                tc.widthType = TableWidthType.DXA
                tc.setWidth(widths[c].toString())
                val p = tc.paragraphs.first()
                p.alignment = cell.alignment
                p.spacingAfter = 0
                addRun(p, cell.text, bold = cell.bold)
            }
        }
    }

    private fun headerCells(labels: List<String>): List<FCell> = labels.map { FCell(it, bold = true) }

    private fun docRow(
        label: String,
        value: String?,
    ): List<FCell> =
        listOf(
            FCell(label),
            FCell(if (value.equals("Oui", ignoreCase = true)) "Oui" else "RAS"),
            FCell(if (value.equals("Non", ignoreCase = true)) "Non" else "RAS"),
        )

    private fun thirds(): List<Int> {
        val third = CONTENT_WIDTH / 3
        return listOf(third, third, CONTENT_WIDTH - 2 * third)
    }

    private fun sumGrouped(values: List<String?>): String {
        val total =
            values
                .mapNotNull { it?.filter(Char::isDigit)?.takeIf { digits -> digits.isNotEmpty() }?.toBigInteger() }
                .fold(BigInteger.ZERO) { acc, value -> acc + value }
        return grouped(total.toBigDecimal())
    }

    private fun thinBorders(table: XWPFTable) {
        val size = 4
        table.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, size, 0, "auto")
        table.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, size, 0, "auto")
        table.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, size, 0, "auto")
        table.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, size, 0, "auto")
        table.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, size, 0, "auto")
        table.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, size, 0, "auto")
    }

    /** Embeds the organisation logo at a fixed height, preserving its aspect ratio (same technique as the traité export). */
    private fun renderLogo(
        paragraph: XWPFParagraph,
        logo: OrganizationLogo,
    ) {
        try {
            val image = ImageIO.read(ByteArrayInputStream(logo.bytes)) ?: return
            val width = (FICHE_LOGO_HEIGHT_PX.toDouble() * image.width / image.height).toInt().coerceAtLeast(1)
            ByteArrayInputStream(logo.bytes).use { stream ->
                paragraph.createRun().addPicture(
                    stream,
                    pictureType(logo.contentType),
                    "organization-logo",
                    Units.pixelToEMU(width),
                    Units.pixelToEMU(FICHE_LOGO_HEIGHT_PX),
                )
            }
        } catch (_: Exception) {
            // A logo that cannot be decoded/embedded must not break the fiche export.
        }
    }

    private fun pictureType(contentType: String): Int =
        when (contentType.lowercase()) {
            "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG
            "image/gif" -> Document.PICTURE_TYPE_GIF
            "image/bmp" -> Document.PICTURE_TYPE_BMP
            else -> Document.PICTURE_TYPE_PNG
        }

    // ---- Shared rendering helpers -------------------------------------------------------

    private fun signatoryName(
        content: Map<String, String>,
        index: Int,
    ): String = content["signataire${index}Nom"].orRas()

    private fun signatoryTitle(
        content: Map<String, String>,
        index: Int,
    ): String = content["signataire${index}Titre"].orRas()

    /**
     * "Monsieur Nom, Titre" — how a signatory appears inside the body prose. The
     * civility prefix only shows when it was entered (the models carry it); an
     * unset civility is simply omitted rather than printing a placeholder.
     */
    private fun signatoryLabel(
        content: Map<String, String>,
        index: Int,
    ): String {
        val civility = content["signataire${index}Civilite"]?.takeIf { it.isNotBlank() }?.let { "$it " } ?: ""
        return "$civility${signatoryName(content, index)}, ${signatoryTitle(content, index)}"
    }

    /**
     * The closing signature block: a full page-width, borderless two-column
     * table. Row 1 holds the two signatories' titles, row 2 their names, all
     * bold. Signatory 1 is pinned left, signatory 2 right, and a generous gap
     * is left between the two rows so the signatures can be handwritten there.
     */
    private fun renderSignatureBlock(
        document: XWPFDocument,
        content: Map<String, String>,
    ) {
        val table = document.createTable(2, 2)
        table.setWidthType(TableWidthType.DXA)
        table.setWidth(CONTENT_WIDTH)
        borderless(table)
        val half = (CONTENT_WIDTH / 2).toString()
        setCell(table.getRow(0).getCell(0), signatoryTitle(content, 1), bold = true, alignment = ParagraphAlignment.LEFT, width = half)
        setCell(table.getRow(0).getCell(1), signatoryTitle(content, 2), bold = true, alignment = ParagraphAlignment.RIGHT, width = half)
        setCell(
            table.getRow(1).getCell(0),
            signatoryName(content, 1),
            bold = true,
            alignment = ParagraphAlignment.LEFT,
            width = half,
            spacingBefore = SIGNATURE_GAP,
        )
        setCell(
            table.getRow(1).getCell(1),
            signatoryName(content, 2),
            bold = true,
            alignment = ParagraphAlignment.RIGHT,
            width = half,
            spacingBefore = SIGNATURE_GAP,
        )
    }

    /**
     * "GNF 238 756 476 (Deux Cent ... Seize Francs Guinéens)" — bold as a whole,
     * since the amount and its currency are entered via the creation form. The
     * raw amount is reduced to its digits first, so a value typed with spaces or
     * thousands separators ("238 756 476") still resolves instead of silently
     * falling back to "RAS". No trailing punctuation: each caller adds its own,
     * since the clause ends a sentence in one document and continues it in another.
     */
    private fun amountClause(
        content: Map<String, String>,
        withCurrencyCode: Boolean = true,
    ): String {
        val digits = content["montant"]?.filter(Char::isDigit)?.takeIf { it.isNotEmpty() }
        val amount = digits?.toBigDecimal() ?: return "RAS"
        val currency = content["devise"]?.takeIf { it.isNotBlank() } ?: "GNF"
        // The attestation de facilité prints the amount without the leading currency code (the words carry the currency).
        val prefix = if (withCurrencyCode) "$currency " else ""
        return "$prefix${grouped(amount)} (${amount.amountInWords(currency)})"
    }

    // ---- Header box ---------------------------------------------------------------------

    /**
     * The double-bordered, 25%-shaded title box every reference template opens
     * with: a fixed-width rectangle holding the title then the reference number,
     * both bold and centered. Pinned in DXA (with the cell at the same width) so
     * it renders as a clean banner rather than shrinking to the text. The
     * attestation matches its model's narrower, page-centered box; the caution
     * spans the full writable width.
     */
    private fun headerBox(
        document: XWPFDocument,
        title: String,
        referenceNumber: String,
        widthDxa: Int = CONTENT_WIDTH,
        centered: Boolean = false,
    ) {
        val table = document.createTable(1, 1)
        table.setWidthType(TableWidthType.DXA)
        table.setWidth(widthDxa)
        if (centered) table.setTableAlignment(TableRowAlign.CENTER)
        val borderSize = 18
        table.setTopBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setBottomBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setLeftBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")
        table.setRightBorder(XWPFTable.XWPFBorderType.DOUBLE, borderSize, 0, "auto")

        val cell = table.getRow(0).getCell(0)
        cell.widthType = TableWidthType.DXA
        cell.setWidth(widthDxa.toString())
        cell.verticalAlignment = XWPFTableCell.XWPFVertAlign.CENTER
        applyPct25Shading(cell)
        val titlePara = cell.paragraphs.first()
        titlePara.alignment = ParagraphAlignment.CENTER
        titlePara.spacingBefore = 80
        addRun(titlePara, title, bold = true, size = TITLE_SIZE)
        val refPara = cell.addParagraph()
        refPara.alignment = ParagraphAlignment.CENTER
        refPara.spacingAfter = 80
        addRun(refPara, "N° $referenceNumber", bold = true, size = TITLE_SIZE)
    }

    /** The reference templates fill the header with a 25% pattern shade (w:shd val="pct25"); replicated here rather than approximated with a solid fill. */
    private fun applyPct25Shading(cell: XWPFTableCell) {
        val tcPr = cell.ctTc.tcPr ?: cell.ctTc.addNewTcPr()
        val shd = if (tcPr.isSetShd) tcPr.shd else tcPr.addNewShd()
        shd.`val` = STShd.PCT_25
        shd.color = "auto"
        shd.fill = "auto"
    }

    // ---- POI helpers (same conventions as the FA/PV/FMP exports) -----------------------

    private fun setUpPage(document: XWPFDocument) {
        val sectPr = document.document.body.addNewSectPr()
        val pageSize = sectPr.addNewPgSz()
        pageSize.w = BigInteger.valueOf(11906)
        pageSize.h = BigInteger.valueOf(16838)
        val margins = sectPr.addNewPgMar()
        margins.left = BigInteger.valueOf(1417)
        margins.right = BigInteger.valueOf(1133)
        margins.top = BigInteger.valueOf(1417)
        margins.bottom = BigInteger.valueOf(1417)
    }

    private fun addRun(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        size: Int = BODY_SIZE,
    ) {
        val run = paragraph.createRun()
        run.fontFamily = FONT
        run.fontSize = size
        run.isBold = bold
        run.setText(text)
    }

    private fun paragraph(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingAfter = 160
        addRun(p, text)
    }

    /** A paragraph built from a sequence of bold/plain runs — how every entered field gets bolded inline with fixed prose. Justified unless a caller overrides it. */
    private fun mixedParagraph(
        document: XWPFDocument,
        vararg segments: Segment,
        alignment: ParagraphAlignment = ParagraphAlignment.BOTH,
    ) {
        val p = document.createParagraph()
        p.alignment = alignment
        p.spacingAfter = 160
        segments.forEach { seg -> addRun(p, seg.text, bold = seg.bold) }
    }

    /** "a) ..." / "b) ..." — the offer-withdrawal clauses print as a lettered list, not plain paragraphs. */
    private fun listItem(
        document: XWPFDocument,
        marker: String,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingAfter = 160
        p.indentationLeft = 360
        p.indentationHanging = 360
        addRun(p, "$marker $text")
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph()
    }

    /** The header's identifying lines (agence, bénéficiaire, date, référence) — bold and centered, unlike the justified body. */
    private fun boldCenteredLine(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.CENTER
        addRun(p, text, bold = true)
    }

    private fun setCell(
        cell: XWPFTableCell,
        text: String,
        bold: Boolean = false,
        alignment: ParagraphAlignment = ParagraphAlignment.LEFT,
        width: String? = null,
        spacingBefore: Int = 0,
    ) {
        width?.let {
            cell.widthType = TableWidthType.DXA
            cell.setWidth(it)
        }
        val p = cell.paragraphs.first()
        p.alignment = alignment
        if (spacingBefore > 0) p.spacingBefore = spacingBefore
        addRun(p, text, bold = bold)
    }

    private fun borderless(table: XWPFTable) {
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
    }

    // ---- Value formatting -------------------------------------------------------------

    private fun String?.orRas(): String = this?.takeIf { it.isNotBlank() } ?: "RAS"

    private fun fmtShort(value: String?): String = value?.let { runCatching { LocalDate.parse(it).format(shortDate) }.getOrNull() } ?: "RAS"

    private fun fmtLong(value: String?): String {
        val date = value?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return "RAS"
        val formatted = date.format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.FRENCH))
        return formatted.split(" ").joinToString(" ") { word -> word.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun grouped(amount: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).format(amount.toBigInteger())
    }

    private companion object {
        const val FONT = "Tahoma"
        const val BODY_SIZE = 11
        const val TITLE_SIZE = 14

        /** Writable page width in twips: A4 (11906) minus the left (1417) and right (1133) margins set in [setUpPage]. */
        const val CONTENT_WIDTH = 9356

        /** The attestation model's header box width in twips (page-centered, narrower than the caution's full-width banner). */
        const val ACF_HEADER_WIDTH = 7896

        /** The attestation de facilité model's header box width in twips (page-centered). */
        const val AFC_HEADER_WIDTH = 6521

        /** Vertical gap (twips, ~45pt) left between a signatory's title and name so the signature can be handwritten between them. */
        const val SIGNATURE_GAP = 900

        /** Height (px) of the organisation logo banner at the head of the Fiche d'approbation; the width follows the image's aspect ratio. */
        const val FICHE_LOGO_HEIGHT_PX = 55
    }
}
