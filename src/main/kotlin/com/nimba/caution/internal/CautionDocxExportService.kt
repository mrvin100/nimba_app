package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionDossierInfo
import com.nimba.caution.CautionFeeBase
import com.nimba.caution.CautionFeeSchedule
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
import java.text.Normalizer
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
        val snapshot = caution.clientSnapshot ?: liveSnapshot(caution.clientId, caution.content["numeroCompte"])

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
        val merged = CautionFieldRegistry.effectiveContent(common, caution.content).toMutableMap()
        // The dossier's objet holds one line per declared lot; keep only this
        // document's line so every renderer below stays unaware of the pairing.
        objetForLot(dossier.content, caution.content["lot"])?.let { merged["objetMarche"] = it }
        return caution.copy(content = merged)
    }

    /**
     * The objet wording that applies to [lot]. The dossier's objet field is a
     * list parallel to its lots: line 1 describes lot 1, line 2 lot 2, and so
     * on. A single line covers every lot (one common wording), and a lot with
     * no matching line falls back to the first, so an incomplete list still
     * produces a document rather than a blank.
     */
    private fun objetForLot(
        dossierContent: Map<String, String>,
        lot: String?,
    ): String? {
        val lines = objetLines(dossierContent)
        if (lines.size <= 1) return lines.firstOrNull()
        val index = declaredLots(dossierContent).indexOfFirst { it.equals(lot?.trim(), ignoreCase = true) }
        return lines.getOrNull(index) ?: lines.first()
    }

    /** The objet field split into its per-lot lines, blanks dropped. */
    private fun objetLines(content: Map<String, String>): List<String> =
        content["objetMarche"]
            ?.split("\n")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /**
     * The live client record projected onto the same shape as the frozen snapshot,
     * for draft previews. [accountNumber] is not part of the client record: it is
     * the dossier's (or standalone document's) own "numeroCompte" common field,
     * resolved by the caller from the content it already has in hand.
     */
    private fun liveSnapshot(
        clientId: UUID,
        accountNumber: String?,
    ): CautionClientSnapshotInfo {
        val client = clients.getOrThrow(clientId)
        return CautionClientSnapshotInfo(
            // A caution can only exist for a client that had a matricule at issuance
            // (enforced on create); orEmpty guards the type only.
            matricule = client.matricule.orEmpty(),
            raisonSociale = client.raisonSociale,
            sigle = client.sigle,
            adressePhysique = client.adressePhysique,
            rccm = client.rccm,
            accountNumber = accountNumber,
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
        val snapshot = liveSnapshot(dossier.clientId, dossier.content["numeroCompte"])
        val currency = lotAmountsOf(dossier).firstOrNull()?.currency ?: DEFAULT_CURRENCY

        val document = XWPFDocument()
        setUpPage(
            document,
            left = NOTIFICATION_MARGIN_LEFT,
            right = NOTIFICATION_MARGIN_RIGHT,
            top = NOTIFICATION_MARGIN_VERTICAL,
            bottom = NOTIFICATION_MARGIN_VERTICAL,
        )
        renderNotification(document, dossier.content, currency, snapshot)

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
        setUpPage(
            document,
            left = FICHE_MARGIN_HORIZONTAL,
            right = FICHE_MARGIN_HORIZONTAL,
            top = FICHE_MARGIN_VERTICAL,
            bottom = FICHE_MARGIN_VERTICAL,
        )
        renderFiche(document, dossier.content, lotAmountsOf(dossier), client, identity.organizationLogo())

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return CautionExport("fiche-approbation-${dossier.referenceNumber}-v${dossier.version}.docx", bytes)
    }

    /**
     * What one lot of the request carries, read from the documents attached to
     * the dossier rather than retyped on the Fiche: the caution issued for the
     * lot (SMS) and the attestation issued for it (ACF/AFC). This is the single
     * entry point the per-lot figures come from, so the Fiche's sollicitations
     * and rentability can never disagree with the documents themselves.
     */
    private data class LotAmounts(
        val label: String,
        val currency: String,
        val caution: BigDecimal?,
        val attestation: BigDecimal?,
    ) {
        fun baseFor(base: CautionFeeBase): BigDecimal? =
            when (base) {
                CautionFeeBase.CAUTION -> caution
                CautionFeeBase.ATTESTATION -> attestation
            }
    }

    /** The lots a dossier declared, in the order they were entered. */
    private fun declaredLots(content: Map<String, String>): List<String> =
        content["lots"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    /**
     * Buckets the dossier's documents by the lot each one covers. A dossier that
     * declared no lot yields one unnamed bucket, so the Fiche keeps its single
     * amount column. A dossier that declared exactly one lot puts every document
     * in it whether or not they name it: there is nothing to disambiguate, and
     * asking the analyst to tag each document would be pure ceremony.
     */
    private fun lotAmountsOf(dossier: CautionDossierInfo): List<LotAmounts> {
        val documents = cautions.dossierDocuments(dossier.id)
        val declared = declaredLots(dossier.content)
        val unambiguous = declared.size <= 1
        val buckets = declared.ifEmpty { listOf(SINGLE_LOT_LABEL) }
        return buckets.map { lot ->
            val ofLot =
                documents.filter { document ->
                    unambiguous || document.content["lot"]?.trim().equals(lot, ignoreCase = true)
                }
            LotAmounts(
                label = lot,
                currency = ofLot.firstNotNullOfOrNull { it.content["devise"]?.takeIf(String::isNotBlank) } ?: DEFAULT_CURRENCY,
                caution = ofLot.firstOrNull { it.documentType == CautionDocumentType.SMS }?.let { amountOf(it.content) },
                attestation =
                    ofLot
                        .firstOrNull {
                            it.documentType == CautionDocumentType.ACF || it.documentType == CautionDocumentType.AFC
                        }?.let { amountOf(it.content) },
            )
        }
    }

    /** A document's entered amount reduced to a number, tolerating the spaces it was typed with. */
    private fun amountOf(content: Map<String, String>): BigDecimal? =
        content["montant"]
            ?.filter(Char::isDigit)
            ?.takeIf { it.isNotEmpty() }
            ?.toBigDecimal()

    /** "Lot 1 : 238 756 476 ; Lot 2 : 190 000 000", or just the amount when the request has a single lot. */
    private fun perLotClause(
        lots: List<LotAmounts>,
        amount: (LotAmounts) -> BigDecimal?,
    ): String {
        val entries = lots.filter { amount(it) != null }
        if (entries.isEmpty()) return "RAS"
        return entries.joinToString(" ; ") { lot ->
            val value = "${lot.currency} ${grouped(requireNotNull(amount(lot)))}"
            if (lot.label == SINGLE_LOT_LABEL) value else "${lot.label} : $value"
        }
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

        boldCenteredLine(document, "AFRILAND FIRST BANK ; AGENCE ${agencyName(agence)}")
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
        renderSignatureBlock(document, c, withCivilityPrefix = true)
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
            bold(caution.referenceNumber),
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
        currency: String,
        snapshot: CautionClientSnapshotInfo,
    ) {
        val refDate = document.createTable(1, 2)
        refDate.setWidthType(TableWidthType.DXA)
        refDate.setWidth(NOTIFICATION_CONTENT_WIDTH)
        borderless(refDate)
        val half = (NOTIFICATION_CONTENT_WIDTH / 2).toString()
        setCell(refDate.getRow(0).getCell(0), notificationReference(content), alignment = ParagraphAlignment.LEFT, width = half)
        setCell(
            refDate.getRow(0).getCell(1),
            "Conakry, le ${fmtLong(content["dateEmission"])}",
            alignment = ParagraphAlignment.RIGHT,
            width = half,
        )

        rightBoldLine(document, snapshot.raisonSociale.orRas())
        rightBoldLine(document, recipientAttention(content))
        content["destinataireNom"]?.takeIf { it.isNotBlank() }?.let { rightBoldLine(document, it) }
        spacer(document)

        // Fixed boilerplate on the real paper template (docs/caution/NOTIFICATION.docx) —
        // never the tender's own subject, which appears later in the body paragraph.
        mixedParagraph(
            document,
            bold("Objet : Notification de caution"),
            alignment = ParagraphAlignment.LEFT,
            spacingAfter = LETTER_SPACING_AFTER,
        )
        // A letter with no customer reference simply omits the line; the model never prints a placeholder there.
        content["vReference"]?.takeIf { it.isNotBlank() }?.let {
            mixedParagraph(document, bold("V/Réf : $it"), alignment = ParagraphAlignment.LEFT, spacingAfter = LETTER_SPACING_AFTER)
        }
        spacer(document)

        // The salutation follows the recipient's own civility, never a fixed "Monsieur".
        val salutation = content["destinataireCivilite"]?.takeIf { it.isNotBlank() } ?: "Monsieur"
        paragraph(document, "$salutation,", spacingAfter = LETTER_SPACING_AFTER)
        paragraph(
            document,
            "Votre correspondance ci-dessus relative à la demande de ${content["demandeResume"].orRas()} dans notre " +
                "institution financière a retenu toute notre attention et nous vous en remercions.",
            spacingAfter = LETTER_SPACING_AFTER,
        )
        paragraph(
            document,
            "Y faisant suite, nous avons le plaisir de vous confirmer que notre comité de crédit compétent pour votre " +
                "dossier a marqué son accord pour votre concours aux conditions suivantes :",
            spacingAfter = LETTER_SPACING_AFTER,
        )

        boldHeading(document, "ARTICULATION DES CONCOURS :")
        multilineParagraphsOrRas(document, content["articulationConcours"])

        boldHeading(document, "II. GARANTIES RETENUES :")
        mixedParagraph(
            document,
            bold("Garanties détenues : "),
            plain(content["garantiesDetenues"].orRas()),
            spacingAfter = LETTER_SPACING_AFTER,
        )
        val toCollect = content["garantiesARecueillir"]?.takeIf { it.isNotBlank() }
        if (toCollect == null) {
            mixedParagraph(document, bold("Garanties à recueillir : "), plain("RAS"), spacingAfter = LETTER_SPACING_AFTER)
        } else {
            mixedParagraph(document, bold("Garanties à recueillir : "), spacingAfter = LETTER_SPACING_AFTER)
            multilineParagraphs(document, toCollect)
        }

        // The very schedule the Fiche's section 6 computes from, printed as prose:
        // the conditions the client is notified of and the ones the bank bills on
        // are one and the same entry, never two.
        boldHeading(document, "III. CONDITIONS DE BANQUE :")
        CautionFeeSchedule.linesOf(content).forEach { line -> labelledLine(document, "${line.describeAsCondition(currency)} ;") }

        paragraph(
            document,
            "Pour la bonne règle, nous vous remercions de bien vouloir accuser réception de la présente, en nous " +
                "retournant la copie ci-jointe, dûment revêtue de votre signature, et précédée de la mention " +
                "« lu et approuvé, bon pour toutes les clauses ci-dessus ».",
            spacingAfter = LETTER_SPACING_AFTER,
        )
        paragraph(
            document,
            "Nous restons à votre entière disposition pour toutes informations complémentaires.",
            spacingAfter = LETTER_SPACING_AFTER,
        )
        paragraph(
            document,
            "Espérant avoir répondu à vos attentes, nous réitérons nos remerciements pour l'intérêt porté à notre " +
                "institution et vous prions d'agréer, $salutation, l'expression de nos salutations distinguées.",
            spacingAfter = LETTER_SPACING_AFTER,
        )
        renderSignatureBlock(document, content, width = NOTIFICATION_CONTENT_WIDTH, gap = NOTIFICATION_SIGNATURE_GAP)
    }

    /**
     * The letter's own reference. Unset, the model's blank pattern is printed for
     * the DCM to complete by hand (`N°/    /AFB/DCM/DGA/26`) rather than a "RAS",
     * which would read as "this letter has no reference".
     */
    private fun notificationReference(content: Map<String, String>): String =
        content["notifReference"]?.takeIf { it.isNotBlank() }
            ?: "N°/        /AFB/DCM/DGA/${LocalDate.now().format(DateTimeFormatter.ofPattern("uu"))}"

    /**
     * "A l'Attention du Directeur Général" / "de la Directrice Administrative" —
     * the article follows the recipient's civility, so a feminine title never
     * prints as "du Directrice".
     */
    private fun recipientAttention(content: Map<String, String>): String {
        val fonction = content["destinataireFonction"]?.takeIf { it.isNotBlank() } ?: "Directeur Général"
        val article = if (content["destinataireCivilite"]?.trim().equals("Madame", ignoreCase = true)) "de la" else "du"
        return "A l'Attention $article $fonction"
    }

    /** Like [multilineParagraphs], but prints "RAS" rather than leaving a heading with nothing under it. */
    private fun multilineParagraphsOrRas(
        document: XWPFDocument,
        value: String?,
    ) {
        if (value.isNullOrBlank()) paragraph(document, "RAS", spacingAfter = LETTER_SPACING_AFTER) else multilineParagraphs(document, value)
    }

    /** A right-aligned bold line, used for the recipient block of the notification. */
    private fun rightBoldLine(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.RIGHT
        p.spacingAfter = LETTER_SPACING_AFTER
        addRun(p, text, bold = true)
    }

    /** A bold, left-aligned section heading (e.g. "ARTICULATION DES CONCOURS :"). */
    private fun boldHeading(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingBefore = 90
        p.spacingAfter = 40
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
            ?.forEach { paragraph(document, it, spacingAfter = LETTER_SPACING_AFTER) }
    }

    /** A line whose label (up to and including the first colon) is bold, the rest plain — the notification's condition lines. */
    private fun labelledLine(
        document: XWPFDocument,
        line: String,
    ) {
        val colon = line.indexOf(':')
        if (colon >= 0) {
            mixedParagraph(
                document,
                bold(line.substring(0, colon + 1)),
                plain(line.substring(colon + 1)),
                spacingAfter = LETTER_SPACING_AFTER,
            )
        } else {
            paragraph(document, line, spacingAfter = LETTER_SPACING_AFTER)
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
        lots: List<LotAmounts>,
        client: ClientInfo,
        logo: OrganizationLogo?,
    ) {
        val logoParagraph = document.createParagraph()
        logoParagraph.alignment = ParagraphAlignment.CENTER
        logo?.let { renderLogo(logoParagraph, it) }
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        addRun(title, "FICHE D'APPROBATION DE CAUTION DE SOUMISSION", bold = true, size = FICHE_TITLE_SIZE)
        spacer(document)

        val dateEntree = client.dateEntreeRelation?.format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))

        ficheSection(document, "1- PRESENTATION DU CLIENT")
        ficheTable(
            document,
            listOf(3600, FICHE_CONTENT_WIDTH - 3600),
            listOf(
                listOf(FCell("CLIENT", bold = true), FCell(client.raisonSociale)),
                listOf(FCell("COMPTE", bold = true), FCell(content["numeroCompte"].orRas())),
                listOf(FCell("AGENCE", bold = true), FCell(agencyName(client.agence))),
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
                    FCell(ficheObjet(content)),
                ),
            ),
        )

        // Both rows read the documents attached to the dossier: the caution issued
        // for each lot, and the attestation issued for it. Nothing is retyped here.
        ficheSection(document, "4- SOLLICITATIONS")
        ficheTable(
            document,
            listOf(3600, FICHE_CONTENT_WIDTH - 3600),
            listOf(
                headerCells(listOf("DESIGNATIONS", "MONTANT")),
                listOf(FCell("CAUTION", bold = true), FCell(perLotClause(lots) { it.caution })),
                listOf(FCell("PROMESSE DE FACILITE", bold = true), FCell(perLotClause(lots) { it.attestation })),
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
        renderFicheConditions(document, content, lots)

        ficheSection(document, "7- APPROBATIONS")
        val approvers = listOf("AE", "DCM", "DRC", "DER", "EXCO")
        val approvColumn = FICHE_CONTENT_WIDTH / approvers.size
        ficheTable(
            document,
            List(approvers.size) { approvColumn },
            listOf(headerCells(approvers), List(approvers.size) { FCell(" ") }),
        )
    }

    /**
     * Section 6's conditions/rentabilité table, one column per lot. Each cell is
     * computed from the dossier's fee schedule applied to the lot's own amount
     * (`max(base × taux, minimum) × (1 + tva)`), so the grid is never keyed in by
     * hand and always agrees with the documents. The left column prints the rule
     * itself, exactly as the paper model does. The TOTAL row is the column sum.
     */
    private fun renderFicheConditions(
        document: XWPFDocument,
        content: Map<String, String>,
        lots: List<LotAmounts>,
    ) {
        val schedule = CautionFeeSchedule.linesOf(content)
        val labelColumn = 3400
        val lotColumn = (FICHE_CONTENT_WIDTH - labelColumn) / lots.size
        val widths = listOf(labelColumn) + List(lots.size) { lotColumn }
        val currency = lots.firstOrNull()?.currency ?: DEFAULT_CURRENCY

        val rows = mutableListOf<List<FCell>>()
        rows.add(
            headerCells(
                listOf("CONDITIONS DE BANQUE") +
                    lots.map { if (it.label == SINGLE_LOT_LABEL) "MT TTC" else "MT TTC ${it.label}" },
            ),
        )
        val computed =
            schedule.map { line ->
                line to lots.map { lot -> lot.baseFor(line.base)?.let(line::amountFor) }
            }
        computed.forEach { (line, amounts) ->
            rows.add(listOf(FCell(line.describe(currency))) + amounts.map { FCell(it?.let(::grouped) ?: "0") })
        }
        rows.add(
            listOf(FCell("TOTAL", bold = true)) +
                lots.indices.map { column ->
                    val total =
                        computed.fold(BigDecimal.ZERO) { acc, (_, amounts) -> acc + (amounts[column] ?: BigDecimal.ZERO) }
                    FCell(grouped(total), bold = true)
                },
        )
        ficheTable(document, widths, rows)
    }

    /**
     * Section 3's OBJET, worded as the paper model does: the objet followed by
     * the lots it covers, `… : Lot 4, Lot 6 et Lot 8.`. When each lot carries
     * its own wording (one objet line per lot), they are listed lot by lot
     * instead, since no single sentence would cover them.
     */
    private fun ficheObjet(content: Map<String, String>): String {
        val lines = objetLines(content)
        val lots = declaredLots(content)
        if (lines.isEmpty()) return "RAS"
        if (lots.isEmpty()) return lines.joinToString(" ; ")
        if (lines.distinct().size == 1) return "${lines.first()} : ${enumerate(lots)}."
        return lots
            .mapIndexed { index, lot -> "$lot : ${lines.getOrNull(index) ?: lines.first()}" }
            .joinToString(" ; ")
    }

    /** "Lot 4, Lot 6 et Lot 8" — the enumeration style the paper templates use. */
    private fun enumerate(values: List<String>): String =
        if (values.size <= 1) {
            values.joinToString("")
        } else {
            "${values.dropLast(1).joinToString(", ")} et ${values.last()}"
        }

    private fun ficheSection(
        document: XWPFDocument,
        title: String,
    ) {
        val p = document.createParagraph()
        p.spacingBefore = 90
        p.spacingAfter = 30
        addRun(p, title, bold = true, size = FICHE_BODY_SIZE)
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
                p.spacingBefore = 0
                p.spacingAfter = 0
                addRun(p, cell.text, bold = cell.bold, size = FICHE_BODY_SIZE)
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
        val third = FICHE_CONTENT_WIDTH / 3
        return listOf(third, third, FICHE_CONTENT_WIDTH - 2 * third)
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
     * Upper-cases the way the bank's templates do: without diacritics. Word's
     * own "Majuscules" effect keeps them, but every reference document prints
     * "DIRECTEUR CREDIT MARKETING", not "DIRECTEUR CRÉDIT MARKETING", so a plain
     * [String.uppercase] would not match the paper.
     */
    private fun upperCaseUnaccented(text: String): String =
        Normalizer
            .normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .uppercase()

    /**
     * An agency's name as the templates print it: upper-cased, and without the
     * "Agence" word when the client record already carries it, so a value typed
     * as "Agence Kaloum" never renders the line as "AGENCE AGENCE KALOUM".
     */
    private fun agencyName(agence: String?): String {
        val name = upperCaseUnaccented(agence.orRas().trim())
        return name.removePrefix("AGENCE ").trim().ifEmpty { name }
    }

    /**
     * "M." / "Mme." — the abbreviated civility the caution's signature block
     * prefixes its names with. An unset or unrecognized civility yields no
     * prefix rather than a placeholder, same rule as [signatoryLabel].
     */
    private fun civilityAbbreviation(civility: String?): String =
        when (civility?.trim()?.lowercase()) {
            "monsieur" -> "M. "
            "madame" -> "Mme. "
            else -> ""
        }

    /**
     * The closing signature block: a full page-width, borderless two-column
     * table. Row 1 holds the two signatories' titles, row 2 their names, all
     * bold. Signatory 1 is pinned left, signatory 2 right, and a generous gap
     * is left between the two rows so the signatures can be handwritten there.
     *
     * Every reference template prints the titles in upper case, whatever the
     * casing they were entered with. [withCivilityPrefix] adds the "M."/"Mme."
     * abbreviation in front of the names: the caution model carries it, the
     * attestations and the notification do not.
     */
    private fun renderSignatureBlock(
        document: XWPFDocument,
        content: Map<String, String>,
        withCivilityPrefix: Boolean = false,
        width: Int = CONTENT_WIDTH,
        gap: Int = SIGNATURE_GAP,
    ) {
        val table = document.createTable(2, 2)
        table.setWidthType(TableWidthType.DXA)
        table.setWidth(width)
        borderless(table)
        val half = (width / 2).toString()

        fun name(index: Int): String {
            val prefix = if (withCivilityPrefix) civilityAbbreviation(content["signataire${index}Civilite"]) else ""
            return "$prefix${signatoryName(content, index)}"
        }

        setCell(
            table.getRow(0).getCell(0),
            upperCaseUnaccented(signatoryTitle(content, 1)),
            bold = true,
            alignment = ParagraphAlignment.LEFT,
            width = half,
        )
        setCell(
            table.getRow(0).getCell(1),
            upperCaseUnaccented(signatoryTitle(content, 2)),
            bold = true,
            alignment = ParagraphAlignment.RIGHT,
            width = half,
        )
        setCell(
            table.getRow(1).getCell(0),
            name(1),
            bold = true,
            alignment = ParagraphAlignment.LEFT,
            width = half,
            spacingBefore = gap,
        )
        setCell(
            table.getRow(1).getCell(1),
            name(2),
            bold = true,
            alignment = ParagraphAlignment.RIGHT,
            width = half,
            spacingBefore = gap,
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

    /**
     * A4 with the given margins, in twips. The caution and the attestations use
     * the templates' defaults; the notification widens its left margin to clear
     * the pre-printed band of the bank's letterhead, and the fiche narrows every
     * margin to stay on a single sheet. See the *_MARGIN constants.
     */
    private fun setUpPage(
        document: XWPFDocument,
        left: Int = MARGIN_LEFT,
        right: Int = MARGIN_RIGHT,
        top: Int = MARGIN_VERTICAL,
        bottom: Int = MARGIN_VERTICAL,
    ) {
        val sectPr = document.document.body.addNewSectPr()
        val pageSize = sectPr.addNewPgSz()
        pageSize.w = BigInteger.valueOf(PAGE_WIDTH.toLong())
        pageSize.h = BigInteger.valueOf(PAGE_HEIGHT.toLong())
        val margins = sectPr.addNewPgMar()
        margins.left = BigInteger.valueOf(left.toLong())
        margins.right = BigInteger.valueOf(right.toLong())
        margins.top = BigInteger.valueOf(top.toLong())
        margins.bottom = BigInteger.valueOf(bottom.toLong())
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
        spacingAfter: Int = BODY_SPACING_AFTER,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingAfter = spacingAfter
        addRun(p, text)
    }

    /** A paragraph built from a sequence of bold/plain runs — how every entered field gets bolded inline with fixed prose. Justified unless a caller overrides it. */
    private fun mixedParagraph(
        document: XWPFDocument,
        vararg segments: Segment,
        alignment: ParagraphAlignment = ParagraphAlignment.BOTH,
        spacingAfter: Int = BODY_SPACING_AFTER,
    ) {
        val p = document.createParagraph()
        p.alignment = alignment
        p.spacingAfter = spacingAfter
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

        /** Space below a body paragraph, in twips. The letter runs tighter so its signatures stay on the first sheet. */
        const val BODY_SPACING_AFTER = 160
        const val LETTER_SPACING_AFTER = 80

        const val PAGE_WIDTH = 11906
        const val PAGE_HEIGHT = 16838

        const val MARGIN_LEFT = 1417
        const val MARGIN_RIGHT = 1133
        const val MARGIN_VERTICAL = 1417

        /** Writable page width in twips: A4 minus the default left/right margins. */
        const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

        /**
         * The notification is printed on the bank's letterhead, whose pre-printed
         * band runs down the left edge. `NOTIFICATION.docx` clears it by indenting
         * its body a further 1418 twips beyond the 1417 margin and letting the text
         * run 624 into the right margin; reproduced here as plain page margins so
         * every paragraph lands in the same block without per-paragraph indents.
         */
        const val NOTIFICATION_MARGIN_LEFT = 2835
        const val NOTIFICATION_MARGIN_RIGHT = 793
        const val NOTIFICATION_CONTENT_WIDTH = PAGE_WIDTH - NOTIFICATION_MARGIN_LEFT - NOTIFICATION_MARGIN_RIGHT

        /** The letter is a one-sheet document: trimmed vertical margins and signature gap keep the signatures off a second page. */
        const val NOTIFICATION_MARGIN_VERTICAL = 964
        const val NOTIFICATION_SIGNATURE_GAP = 200

        /** The fiche is an internal one-sheet form: tighter margins, smaller type, so its seven sections never spill onto a second page. */
        const val FICHE_MARGIN_HORIZONTAL = 1134
        const val FICHE_MARGIN_VERTICAL = 851
        const val FICHE_CONTENT_WIDTH = PAGE_WIDTH - 2 * FICHE_MARGIN_HORIZONTAL
        const val FICHE_BODY_SIZE = 9
        const val FICHE_TITLE_SIZE = 12

        /** The attestation model's header box width in twips (page-centered, narrower than the caution's full-width banner). */
        const val ACF_HEADER_WIDTH = 7896

        /** The attestation de facilité model's header box width in twips (page-centered). */
        const val AFC_HEADER_WIDTH = 6521

        /** Vertical gap (twips, ~45pt) left between a signatory's title and name so the signature can be handwritten between them. */
        const val SIGNATURE_GAP = 900

        /** Height (px) of the organisation logo banner at the head of the Fiche d'approbation; the width follows the image's aspect ratio. */
        const val FICHE_LOGO_HEIGHT_PX = 38

        /** The single, unnamed bucket a request that declared no lot falls into — its Fiche keeps one amount column. */
        const val SINGLE_LOT_LABEL = ""

        /** The currency a dossier's figures default to when no document has stated one yet. */
        const val DEFAULT_CURRENCY = "GNF"

        /** Combining marks left behind by NFD normalization, stripped so an upper-cased title matches the paper templates. */
        val DIACRITICS = "\\p{M}+".toRegex()
    }
}
