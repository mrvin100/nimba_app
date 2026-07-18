package com.nimba.analysissheet.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.amortizationschedule.ScheduleLineInfo
import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.FaObservation
import com.nimba.analysissheet.FaObservationsProvider
import com.nimba.analysissheet.FaPilier
import com.nimba.analysissheet.FaSectionDefaults
import com.nimba.analysissheet.FaSectionInfo
import com.nimba.analysissheet.FaSectionKey
import com.nimba.analysissheet.FaSectionRegistry
import com.nimba.analysissheet.FaSectionType
import com.nimba.creditcase.CaseTypePolicies
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.guarantee.GuaranteeInfo
import com.nimba.guarantee.GuaranteeKind
import com.nimba.guarantee.GuaranteeModuleApi
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.imageio.ImageIO

/** One exported Fiche d'analyse document, ready to stream back as a download. */
data class AnalysisSheetExport(
    val filename: String,
    val content: ByteArray,
)

/**
 * Builds the Word (.docx) export of a case's Fiche d'analyse as an exact
 * replica of the bank's real documents (docs/nimba-fa-document-spec.md;
 * ground truth: the UHODA and IKT files in docs/refinements): A4, the
 * template's margins, Tahoma justified body, bordered tables, the cover
 * 2×2 table + avis blocks, then the piliers in document order — §3.5 reprints
 * the full imported échéancier. Every value is printed as-is with "RAS" for
 * anything not yet captured, so the document is meaningful whether the FA was
 * never started, is a draft, or is published. Read-only: never mutates the case.
 */
@Service
class AnalysisSheetDocxExportService(
    private val analysisSheets: AnalysisSheetModuleApi,
    private val creditCases: CreditCaseModuleApi,
    private val guarantees: GuaranteeModuleApi,
    private val amortizationSchedules: AmortizationScheduleModuleApi,
    private val observationsProvider: FaObservationsProvider,
    private val images: AnalysisSheetImageService,
    private val objectMapper: ObjectMapper,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    @Transactional(readOnly = true)
    fun export(creditCaseId: UUID): AnalysisSheetExport {
        val case = creditCases.getOrThrow(creditCaseId)
        val sheet = analysisSheets.findByCase(creditCaseId)
        val sections = analysisSheets.sections(creditCaseId).ifEmpty { skeletonFor(case) }
        val ctx =
            RenderContext(
                case = case,
                sheetId = sheet?.id,
                sections = sections.associateBy { it.key },
                taSummary = amortizationSchedules.scheduleSummary(creditCaseId),
                taLines = amortizationSchedules.scheduleLines(creditCaseId),
                guarantees = guarantees.listByCase(creditCaseId),
            )

        val document = XWPFDocument()
        setUpPage(document)
        // The template leaves blank lines above the cover so the first page can
        // be printed on the bank's letterhead paper.
        repeat(4) { spacer(document) }
        renderCover(document, ctx)
        renderAvis(document, ctx.identity)
        listOf(FaPilier.PILIER_1, FaPilier.PILIER_2, FaPilier.PILIER_3, FaPilier.PILIER_4).forEach { pilier ->
            renderPilier(document, pilier, ctx)
        }
        renderConclusion(document, ctx)
        renderObservations(document, observationsProvider.observationsFor(creditCaseId))
        renderAnnexes(document, ctx)

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return AnalysisSheetExport("fiche-analyse-${case.caseNumber}.docx", bytes)
    }

    /**
     * The registry skeleton (with prefills) when the FA was never initiated —
     * the export still prints every bound and computed value the dossier holds.
     */
    private fun skeletonFor(case: CreditCaseInfo): List<FaSectionInfo> {
        val variant = CaseTypePolicies.find(case.productType, case.contractType)?.faVariant ?: return emptyList()
        return FaSectionRegistry.sectionsFor(variant).map { key ->
            FaSectionInfo(key, key.pilier, key.type, key.label, defaultContentJson = FaSectionDefaults.defaultContentFor(key))
        }
    }

    private inner class RenderContext(
        val case: CreditCaseInfo,
        val sheetId: UUID?,
        val sections: Map<FaSectionKey, FaSectionInfo>,
        val taSummary: ScheduleSummary?,
        val taLines: List<ScheduleLineInfo>,
        val guarantees: List<GuaranteeInfo>,
    ) {
        val identity: ClientIdentityInfo get() = case.clientIdentity
        val conditions: ConditionsDeBanqueInfo get() = case.conditionsDeBanque

        fun content(key: FaSectionKey): String? = sections[key]?.let { it.contentJson ?: it.defaultContentJson }

        fun has(key: FaSectionKey): Boolean = key in sections
    }

    // ---- Cover ------------------------------------------------------------------------

    private fun renderCover(
        document: XWPFDocument,
        ctx: RenderContext,
    ) {
        val demande = keyValues(ctx.content(FaSectionKey.COVER_INFOS_DEMANDE))
        val internes = keyValues(ctx.content(FaSectionKey.COVER_INFOS_INTERNES))

        val table = document.createTable(2, 2)
        table.setWidth("100%")

        val left = table.getRow(0).getCell(0)
        cellTitle(left, "INFORMATIONS SUR LA DEMANDE")
        cellLine(left, "Raison sociale", ctx.case.clientName)
        cellLine(left, "Référence de la demande", demande["reference"])
        cellLine(left, "Date de réception demande", demande["dateReception"])
        cellLine(
            left,
            "Montant financement sollicité",
            demande["montantSollicite"] ?: ctx.taSummary?.let { "${grouped(it.loanAmount)} ${ctx.case.currency}" },
        )
        cellLine(left, "Équipement", demande["equipement"])
        cellLine(left, "Concessionnaire", demande["concessionnaire"])
        cellLine(
            left,
            "1er loyer TTC",
            demande["premierLoyerTtc"] ?: ctx.taSummary?.premierLoyerTtc?.let { "${grouped(it)} ${ctx.case.currency}" },
        )
        cellLine(left, "Risque global", demande["risqueGlobal"])
        cellLine(left, "Comité de crédit compétent", demande["comiteCompetent"])
        cellLine(left, "Nature de la demande", demande["natureDemande"] ?: "Leasing")

        val right = table.getRow(0).getCell(1)
        cellTitle(right, "INFORMATIONS INTERNES")
        cellLine(right, "Agence", ctx.identity.agence)
        cellLine(right, "Gestionnaire", ctx.identity.gestionnaire)
        cellLine(right, "Analyste", ctx.identity.analyste)
        cellLine(right, "Date de la dernière visite de fidélisation ou de prospection", fmt(ctx.identity.dateDerniereVisite))
        cellLine(right, "Date d'entrée en relation", fmt(ctx.identity.dateEntreeRelation))
        cellLine(right, "Derniers états financiers reçus", internes["derniersEtatsFinanciers"])
        cellLine(right, "Cotation précédente du client", ctx.identity.cotationPrecedente)
        cellLine(right, "Cotation actuelle du client", ctx.identity.cotationActuelle)
        cellLine(right, "N° de compte", ctx.case.accountNumber)
        cellLine(right, "Frais d'étude dossier", internes["fraisEtudeDossier"])

        mergeRow(table, 1)
        val proposition = table.getRow(1).getCell(0)
        cellTitle(proposition, "PROPOSITIONS DE DECISION DE L'ANALYSTE")
        (ctx.content(FaSectionKey.COVER_PROPOSITION) ?: "RAS").lines().forEach { line ->
            cellText(proposition, line)
        }
        cellText(proposition, "")
        cellTitle(proposition, "CONDITIONS DE BANQUE :")
        conditionsLines(ctx).forEach { cellText(proposition, it) }
        cellText(proposition, "")
        cellTitle(proposition, "GARANTIES")
        guaranteeLines(ctx.guarantees).forEach { (text, bold) ->
            if (bold) cellTitle(proposition, text) else cellText(proposition, text)
        }
    }

    private fun renderAvis(
        document: XWPFDocument,
        identity: ClientIdentityInfo,
    ) {
        spacer(document)
        val agence = identity.agence?.uppercase(Locale.FRENCH)?.let { "AVIS DE L'AGENCE $it" } ?: "AVIS DE L'AGENCE"
        val table = document.createTable(2, 1)
        table.setWidth("100%")
        listOf(
            agence to "Nom et signature du CAK :",
            "AVIS DE LA DIRECTION RECHERCHE ET INVESTISSEMENT" to "Nom et signature du DRI :",
        ).forEachIndexed { index, (title, signature) ->
            val cell = table.getRow(index).getCell(0)
            cellTitle(cell, title)
            cellText(cell, "Date :")
            cellText(cell, signature)
            cellText(cell, "")
        }
    }

    private fun conditionsLines(ctx: RenderContext): List<String> {
        val conditions = ctx.conditions
        val ta = ctx.taSummary
        val lines =
            mutableListOf(
                "1er loyer : ${ta?.premierLoyerTtc?.let { "${grouped(it)} ${ctx.case.currency}" } ?: "RAS"}",
                "Loyer mensuel à payer : ${ta?.loyerMensuelHt?.let { "${grouped(it)} ${ctx.case.currency}" } ?: "RAS"}",
                "Frais de mise en place : ${pct(conditions.fraisMiseEnPlacePct)} HT",
                "Com d'engagement : ${pct(conditions.comEngagementPct)} HT",
                "Frais d'études de dossiers : ${pct(conditions.fraisEtudesPct)} HT",
                "Durée du leasing : ${ta?.durationMonths?.let { "$it mois" } ?: "RAS"}",
                "Valeur résiduelle : ${pct(conditions.valeurResiduellePct)} HT",
            )
        fraisDiversItems(conditions.fraisDivers).forEach { lines += "${it.label} : ${grouped(it.montant)}" }
        return lines
    }

    private fun guaranteeLines(caseGuarantees: List<GuaranteeInfo>): List<Pair<String, Boolean>> {
        val detenues = caseGuarantees.filter { it.kind == GuaranteeKind.DETENUE }
        val aRecueillir = caseGuarantees.filter { it.kind == GuaranteeKind.A_RECUEILLIR }
        val lines = mutableListOf<Pair<String, Boolean>>()
        lines += "GARANTIES DETENUES :" to true
        if (detenues.isEmpty()) lines += "RAS" to false else detenues.forEach { lines += "${it.description.orRas()} ;" to false }
        lines += "" to false
        lines += "GARANTIES A RECUEILLIR" to true
        if (aRecueillir.isEmpty()) lines += "RAS" to false else aRecueillir.forEach { lines += "${it.description.orRas()} ;" to false }
        return lines
    }

    // ---- Piliers ----------------------------------------------------------------------

    private fun renderPilier(
        document: XWPFDocument,
        pilier: FaPilier,
        ctx: RenderContext,
    ) {
        val sections = ctx.sections.values.filter { it.pilier == pilier }
        if (sections.isEmpty()) return
        spacer(document)
        pilierHeading(document, pilierTitle(pilier), centered = false)
        sections.forEach { section -> renderSection(document, section, ctx) }
    }

    private fun pilierTitle(pilier: FaPilier): String =
        when (pilier) {
            FaPilier.COVER -> ""
            FaPilier.PILIER_1 -> "PILIER 1 : CONNAISSANCE DE L'ENTREPRISE"
            FaPilier.PILIER_2 -> "PILIER 2 : ANALYSE DU MARCHE"
            FaPilier.PILIER_3 -> "PILIER 3 : ANALYSE FINANCIERE"
            FaPilier.PILIER_4 -> "4. SYNTHESE DES RISQUES ET PRESENTATION DES SURETES"
            FaPilier.CONCLUSION -> "V – CONCLUSION"
            FaPilier.ANNEXES -> "ANNEXES"
        }

    private fun renderSection(
        document: XWPFDocument,
        section: FaSectionInfo,
        ctx: RenderContext,
    ) {
        sectionHeading(document, section.label.uppercase(Locale.FRENCH))
        val content = section.contentJson ?: section.defaultContentJson
        when (section.type) {
            FaSectionType.NARRATIVE -> narrative(document, content)
            FaSectionType.TABLE -> renderTypedTable(document, section.key, content)
            FaSectionType.KEY_VALUE -> renderKeyValue(document, section.key, content)
            FaSectionType.FLEX_TABLE -> renderFlexTable(document, content)
            FaSectionType.FINANCIAL -> renderFinancial(document, content)
            FaSectionType.IMAGE -> renderImageSection(document, section.key, content, ctx)
            FaSectionType.BOUND -> renderBound(document, section.key, ctx)
            FaSectionType.COMPUTED -> renderComputed(document, section.key, ctx)
        }
    }

    // ---- Section type renderers -------------------------------------------------------

    private fun narrative(
        document: XWPFDocument,
        text: String?,
    ) {
        (text?.takeIf { it.isNotBlank() } ?: "RAS").lines().forEach { line ->
            paragraph(document, line)
        }
    }

    /** The typed columns of every TABLE section: content field → printed header. */
    private fun tableColumns(key: FaSectionKey): List<Pair<String, String>> =
        when (key) {
            FaSectionKey.PILIER1_SIGNATAIRES ->
                listOf("nom" to "NOMS ET PRENOMS", "piece" to "REFERENCE DE LA PIECE D'IDENTITE", "validite" to "VALIDITE")
            FaSectionKey.PILIER1_MOUVEMENTS -> listOf("annee" to "ANNEE", "montant" to "TOTAL MOUVEMENT CONFIE")
            FaSectionKey.PILIER1_RENTABILITE_COMPTE -> listOf("nature" to "NATURE", "montant" to "MONTANT")
            FaSectionKey.PILIER1_ACTIONNARIAT ->
                listOf(
                    "nom" to "Nom ou raison sociale",
                    "capital" to "Capital détenu",
                    "actions" to "Nombre d'actions",
                    "pourcentage" to "Pourcentage",
                    "nationalite" to "Nationalité",
                    "observations" to "Observations",
                )
            FaSectionKey.PILIER1_MORALITE ->
                listOf(
                    "nom" to "Nom et Prénom",
                    "casier" to "Casier judiciaire",
                    "interditBancaire" to "Interdit bancaire",
                    "listeNoire" to "Liste noire",
                    "respectEngagements" to "Respect des engagements",
                    "reputation" to "Réputation sociale",
                )
            FaSectionKey.PILIER1_PERSONNES_CLES ->
                listOf("nom" to "NOMS ET PRENOMS", "fonction" to "FONCTIONS OCCUPEES", "experience" to "EXPERIENCE")
            FaSectionKey.PILIER1_RELATIONS_BANCAIRES ->
                listOf("banque" to "BANQUES", "mouvementExercice" to "Mouvement exercice N-1", "mouvementsEnCours" to "Mouvements en cours")
            FaSectionKey.PILIER1_LOGISTIQUE ->
                listOf("designation" to "DESIGNATION", "quantite" to "QUANTITE", "observation" to "OBSERVATION")
            FaSectionKey.PILIER1_CLIENTS ->
                listOf("designation" to "DESIGNATION", "localisation" to "LOCALISATION", "chiffreAffaires" to "CA SUR EXOS N-1")
            FaSectionKey.PILIER1_FOURNISSEURS -> listOf("designation" to "DESIGNATION", "contacts" to "CONTACTS")
            FaSectionKey.PILIER1_CONTRATS_REALISES ->
                listOf(
                    "nature" to "NATURE DES TRAVAUX",
                    "maitreOuvrage" to "MAITRE D'OUVRAGE",
                    "adresseLivraison" to "ADRESSE DE LIVRAISON",
                    "dateLivraison" to "DATE DE LIVRAISON",
                )
            FaSectionKey.PILIER1_ENGAGEMENTS_NOS_LIVRES ->
                listOf(
                    "nature" to "Nature du concours",
                    "autorisation" to "Autorisation",
                    "taux" to "Taux",
                    "encours" to "Encours",
                    "echeance" to "Échéance",
                )
            FaSectionKey.PILIER1_SYNTHESE_PAYEUR -> listOf("indicateur" to "INDICATEURS", "commentaire" to "COMMENTAIRE")
            FaSectionKey.PILIER2_ENCAISSEMENTS ->
                listOf(
                    "periode" to "PERIODE",
                    "numeroCheque" to "N° CHEQUE",
                    "banque" to "BANQUE",
                    "montant" to "MONTANT",
                    "observation" to "OBSERVATION",
                )
            FaSectionKey.PILIER3_CEP -> listOf("element" to "ELEMENTS", "unMois" to "1 Mois", "douzeMois" to "12 Mois")
            FaSectionKey.PILIER4_RISQUES ->
                listOf("nature" to "Nature du risque", "facteurs" to "Facteurs de risque", "mesures" to "Mesures de mitigation")
            else -> emptyList()
        }

    private fun renderTypedTable(
        document: XWPFDocument,
        key: FaSectionKey,
        content: String?,
    ) {
        val parsed = tableContent(content)
        parsed.narrative?.takeIf { it.isNotBlank() }?.let { narrative(document, it) }
        val columns = tableColumns(key)
        if (parsed.rows.isEmpty() || columns.isEmpty()) {
            if (parsed.narrative.isNullOrBlank()) paragraph(document, "RAS")
        } else {
            dataTable(
                document,
                columns.map { it.second },
                parsed.rows.map { row -> columns.map { (field, _) -> row[field].str() } },
            )
        }
        commentaire(document, parsed.commentaire)
    }

    /** The fixed labeled fields of every KEY_VALUE section: content field → printed label. */
    private fun keyValueFields(key: FaSectionKey): List<Pair<String, String>> =
        when (key) {
            FaSectionKey.PILIER2_CONTRAT ->
                listOf(
                    "dateEnregistrement" to "DATE D'ENREGISTREMENT",
                    "natureTravaux" to "NATURE DES TRAVAUX",
                    "dateSignature" to "DATE DE SIGNATURE",
                    "maitreOuvrage" to "MAITRE D'OUVRAGE",
                    "delaisExecution" to "DELAIS D'EXECUTION",
                    "modalitePaiement" to "MODALITE DE PAIEMENT",
                    "delaiPaiement" to "DELAI DE PAIEMENT",
                    "domiciliation" to "DOMICILIATION",
                    "conditionsSuspensives" to "CONDITIONS SUSPENSIVES DU CONTRAT",
                    "conditionPaiement" to "CONDITION DE PAIEMENT",
                )
            else -> emptyList()
        }

    private fun renderKeyValue(
        document: XWPFDocument,
        key: FaSectionKey,
        content: String?,
    ) {
        val fields = keyValueFields(key)
        if (fields.isEmpty()) {
            // COVER_INFOS_* are consumed by the cover renderer; anything else
            // without a field list falls back to RAS.
            paragraph(document, "RAS")
            return
        }
        val values = keyValues(content)
        val table = document.createTable(fields.size, 2)
        table.setWidth("100%")
        fields.forEachIndexed { index, (field, label) ->
            setCell(table.getRow(index).getCell(0), label, bold = true)
            setCell(table.getRow(index).getCell(1), values[field].orRas())
        }
        spacer(document)
    }

    private fun renderFlexTable(
        document: XWPFDocument,
        content: String?,
    ) {
        val parsed: FlexTableContent =
            content?.let { runCatching { objectMapper.readValue<FlexTableContent>(it) }.getOrNull() } ?: FlexTableContent()
        parsed.narrative?.takeIf { it.isNotBlank() }?.let { narrative(document, it) }
        if (parsed.columns.isNotEmpty() && parsed.rows.isNotEmpty()) {
            dataTable(document, parsed.columns, parsed.rows.map { row -> parsed.columns.indices.map { row.getOrNull(it).str() } })
        } else if (parsed.narrative.isNullOrBlank()) {
            paragraph(document, "RAS")
        }
        commentaire(document, parsed.commentaire)
    }

    private fun renderFinancial(
        document: XWPFDocument,
        content: String?,
    ) {
        val parsed: FinancialContent =
            content?.let { runCatching { objectMapper.readValue<FinancialContent>(it) }.getOrNull() } ?: FinancialContent()
        if (parsed.lines.isEmpty()) {
            paragraph(document, "RAS")
        } else {
            dataTable(
                document,
                listOf("") + parsed.years,
                parsed.lines.map { line -> listOf(line.label.orEmpty()) + parsed.years.indices.map { line.values.getOrNull(it).str() } },
            )
        }
        commentaire(document, parsed.commentaire)
    }

    private fun renderImageSection(
        document: XWPFDocument,
        key: FaSectionKey,
        content: String?,
        ctx: RenderContext,
    ) {
        val parsed: ImageContent =
            content?.let { runCatching { objectMapper.readValue<ImageContent>(it) }.getOrNull() } ?: ImageContent()
        val figures = ctx.sheetId?.let { images.sectionImageObjects(it, key) }.orEmpty()
        parsed.narrative?.takeIf { it.isNotBlank() }?.let { narrative(document, it) }
        figures.forEach { (info, bytes) ->
            embedImage(document, info.contentType, info.fileName, bytes)
            info.caption?.let { caption ->
                val p = document.createParagraph()
                p.alignment = ParagraphAlignment.CENTER
                run(p, caption, italic = true)
            }
        }
        if (parsed.narrative.isNullOrBlank() && figures.isEmpty()) paragraph(document, "RAS")
        commentaire(document, parsed.commentaire)
    }

    // ---- BOUND sections ---------------------------------------------------------------

    private fun renderBound(
        document: XWPFDocument,
        key: FaSectionKey,
        ctx: RenderContext,
    ) {
        when (key) {
            FaSectionKey.PILIER1_INFOS_GENERALES -> renderIdentityList(document, ctx)
            FaSectionKey.PILIER1_REGULARITE -> renderRegularite(document, ctx)
            FaSectionKey.PILIER4_SURETES -> renderSuretes(document, ctx.guarantees)
            else -> paragraph(document, "RAS")
        }
    }

    private fun renderIdentityList(
        document: XWPFDocument,
        ctx: RenderContext,
    ) {
        val identity = ctx.identity
        val pairs =
            listOf(
                "RAISON SOCIALE" to ctx.case.clientName,
                "FORME JURIDIQUE" to identity.formeJuridique,
                "DATE DE CREATION" to fmt(identity.dateCreation),
                "ADRESSE PHYSIQUE" to identity.adressePhysique,
                "ACTIVITE DE BASE" to identity.activiteDeBase,
                "CODE NIF" to identity.codeNif,
                "PRINCIPAL DIRIGEANT" to identity.principalDirigeant,
                "N° COMPTE" to ctx.case.accountNumber,
                "DATE ENTREE EN RELATION" to fmt(identity.dateEntreeRelation),
                "DATE DERNIERE VISITE" to fmt(identity.dateDerniereVisite),
                "AGENCE" to identity.agence,
                "GESTIONNAIRE" to identity.gestionnaire,
                "ANALYSTE" to identity.analyste,
            )
        val table = document.createTable(pairs.size, 2)
        table.setWidth("100%")
        borderless(table)
        pairs.forEachIndexed { index, (label, value) ->
            setCell(table.getRow(index).getCell(0), label, bold = true)
            setCell(table.getRow(index).getCell(1), ": ${value.orRas()}")
        }
        spacer(document)
    }

    private fun renderRegularite(
        document: XWPFDocument,
        ctx: RenderContext,
    ) {
        val table = document.createTable(3, 2)
        table.setWidth("100%")
        listOf(
            "INTITULE DU COMPTE" to ctx.case.clientName,
            "NUMERO DE COMPTE" to ctx.case.accountNumber.orRas(),
            "DATE D'OUVERTURE DU COMPTE" to fmt(ctx.identity.dateEntreeRelation).orRas(),
        ).forEachIndexed { index, (label, value) ->
            setCell(table.getRow(index).getCell(0), label, bold = true)
            setCell(table.getRow(index).getCell(1), value)
        }
        spacer(document)
    }

    private fun renderSuretes(
        document: XWPFDocument,
        caseGuarantees: List<GuaranteeInfo>,
    ) {
        paragraph(document, "Pour couvrir les engagements du client nous proposons la constitution des garanties ci-dessous :")
        guaranteeLines(caseGuarantees).forEach { (text, bold) ->
            if (text.isNotBlank()) {
                val p = document.createParagraph()
                p.alignment = ParagraphAlignment.BOTH
                run(p, text, bold = bold)
            }
        }
    }

    // ---- COMPUTED sections ------------------------------------------------------------

    private fun renderComputed(
        document: XWPFDocument,
        key: FaSectionKey,
        ctx: RenderContext,
    ) {
        when (key) {
            FaSectionKey.PILIER3_RENTABILITE_BANQUE -> renderRentabilite(document, ctx)
            FaSectionKey.PILIER3_SIMULATION_FINANCEMENT -> renderSimulation(document, ctx)
            else -> paragraph(document, "RAS")
        }
    }

    private fun renderRentabilite(
        document: XWPFDocument,
        ctx: RenderContext,
    ) {
        val ta = ctx.taSummary
        if (ta == null) {
            paragraph(document, "RAS")
            return
        }
        val conditions = ctx.conditions
        val fraisEtude = fee(ta.loanAmount, conditions.fraisEtudesPct)
        val fraisMep = fee(ta.loanAmount, conditions.fraisMiseEnPlacePct)
        val comEngagement = fee(ta.loanAmount, conditions.comEngagementPct)
        val vr = ta.valeurResiduelle ?: BigDecimal.ZERO
        val totalMep = listOfNotNull(fraisEtude, fraisMep, comEngagement).fold(BigDecimal.ZERO, BigDecimal::add)
        val total = ta.totalInteret + totalMep + vr

        dataTable(
            document,
            listOf("Désignation", "Taux", "Montant HT"),
            listOf(
                listOf("TAUX D'INTERET/an", pct(conditions.tauxInteretPct), ""),
                listOf("Besoin de financement", "", grouped(ta.loanAmount)),
                listOf("Durée maximum", "${ta.durationMonths} mois", ""),
                listOf("INTERET sur ${ta.durationMonths} mois", "", grouped(ta.totalInteret)),
                listOf("Frais d'étude dossier crédit", pct(conditions.fraisEtudesPct), fraisEtude?.let { grouped(it) } ?: "RAS"),
                listOf("Frais de mise en place", pct(conditions.fraisMiseEnPlacePct), fraisMep?.let { grouped(it) } ?: "RAS"),
                listOf("Commissions d'engagement", pct(conditions.comEngagementPct), comEngagement?.let { grouped(it) } ?: "RAS"),
                listOf("VALEUR RESIDUELLE", pct(conditions.valeurResiduellePct), grouped(vr)),
                listOf("TOTAL PRODUIT", "", grouped(total)),
            ),
        )
        commentaire(
            document,
            "L'opération dégage un total produit collecté de ${ctx.case.currency} ${grouped(totalMep)} HT à la mise en place, " +
                "une valeur résiduelle de ${ctx.case.currency} ${grouped(vr)} et en lui octroyant le financement au taux de " +
                "${pct(conditions.tauxInteretPct)} nous récoltons ${ctx.case.currency} ${grouped(ta.totalInteret)} d'intérêts, " +
                "soit un total de ${ctx.case.currency} ${grouped(total)} HT de produit collecté.",
        )
    }

    private fun renderSimulation(
        document: XWPFDocument,
        ctx: RenderContext,
    ) {
        if (ctx.taLines.isEmpty()) {
            paragraph(document, "RAS")
            return
        }
        dataTable(
            document,
            listOf("N°", "Date échéance", "Loyer HT", "Taxes", "Loyer TTC", "Intérêts", "Capital", "Capital restant dû"),
            ctx.taLines.map { line ->
                listOf(
                    line.numeroEcheance,
                    fmt(line.dateEcheance).orEmpty(),
                    grouped(line.loyerHt),
                    grouped(line.taxes),
                    grouped(line.loyerTtc),
                    grouped(line.interet),
                    grouped(line.capital),
                    line.capitalRestantDu?.let { grouped(it) }.orEmpty(),
                )
            },
            fontSize = 9,
        )
    }

    // ---- Conclusion & annexes ---------------------------------------------------------

    private fun renderConclusion(
        document: XWPFDocument,
        ctx: RenderContext,
    ) {
        if (ctx.sections.values.none { it.pilier == FaPilier.CONCLUSION }) return
        spacer(document)
        pilierHeading(document, pilierTitle(FaPilier.CONCLUSION), centered = true)

        val table = document.createTable(6, 2)
        table.setWidth("100%")
        setCell(table.getRow(0).getCell(0), "POINTS FORTS", bold = true)
        setCell(table.getRow(0).getCell(1), "POINTS FAIBLES", bold = true)
        cellMultiline(table.getRow(1).getCell(0), ctx.content(FaSectionKey.CONCLUSION_POINTS_FORTS).orRas())
        cellMultiline(table.getRow(1).getCell(1), ctx.content(FaSectionKey.CONCLUSION_POINTS_FAIBLES).orRas())

        mergeRow(table, 2)
        val opportunites = table.getRow(2).getCell(0)
        cellTitle(opportunites, "OPPORTUNITES A SAISIR")
        ctx
            .content(FaSectionKey.CONCLUSION_OPPORTUNITES)
            .orRas()
            .lines()
            .forEach { cellText(opportunites, it) }

        mergeRow(table, 3)
        val articulation = table.getRow(3).getCell(0)
        cellTitle(articulation, "ARTICULATION DU FINANCEMENT A ACCORDER")
        articulationLines(ctx).forEach { cellText(articulation, it) }

        mergeRow(table, 4)
        val garanties = table.getRow(4).getCell(0)
        guaranteeLines(ctx.guarantees).forEach { (text, bold) ->
            if (bold) cellTitle(garanties, text) else cellText(garanties, text)
        }

        mergeRow(table, 5)
        val conditions = table.getRow(5).getCell(0)
        cellTitle(conditions, "CONDITIONS DE BANQUE")
        conditionsLines(ctx).forEach { cellText(conditions, it) }
    }

    private fun articulationLines(ctx: RenderContext): List<String> {
        val ta = ctx.taSummary ?: return listOf("RAS")
        val currency = ctx.case.currency
        val lines =
            mutableListOf(
                "Un crédit-bail sur ${ta.durationMonths} mois d'un montant de $currency ${grouped(ta.loanAmount)} soit :",
                "$currency ${grouped(ta.totalEquipement)} pour l'acquisition de l'équipement ;",
            )
        if (ta.totalAssurance.signum() != 0) {
            lines += "$currency ${grouped(ta.totalAssurance)} pour l'assurance tous risques pendant la période du leasing ;"
        }
        if (ta.totalTracking.signum() != 0) {
            lines += "$currency ${grouped(ta.totalTracking)} pour le tracking pendant la période du leasing ;"
        }
        if (ta.totalImmatriculation.signum() != 0) {
            lines += "$currency ${grouped(ta.totalImmatriculation)} pour l'immatriculation."
        }
        return lines
    }

    /** The A_COMPLETER loop's table — generated from the workflow, never typed by hand. */
    private fun renderObservations(
        document: XWPFDocument,
        observations: List<FaObservation>,
    ) {
        if (observations.isEmpty()) return
        spacer(document)
        sectionHeading(document, "LES OBSERVATIONS SUR LE DOSSIER LORS DU DERNIER COMITE DE CREDIT")
        dataTable(
            document,
            listOf("OBSERVATIONS", "ELEMENTS DE REPONSE"),
            observations.map { listOf(it.observation, if (it.resolved) "Ok" else "Encours") },
        )
    }

    private fun renderAnnexes(
        document: XWPFDocument,
        ctx: RenderContext,
    ) {
        renderPilier(document, FaPilier.ANNEXES, ctx)
    }

    // ---- POI helpers ------------------------------------------------------------------

    private fun setUpPage(document: XWPFDocument) {
        val sectPr = document.document.body.addNewSectPr()
        val pageSize = sectPr.addNewPgSz()
        pageSize.w = BigInteger.valueOf(11906) // A4 portrait, in twips
        pageSize.h = BigInteger.valueOf(16838)
        val margins = sectPr.addNewPgMar()
        margins.left = BigInteger.valueOf(1418) // 2.50 cm — the template's geometry
        margins.right = BigInteger.valueOf(992) // 1.75 cm
        margins.top = BigInteger.valueOf(567) // 1.00 cm
        margins.bottom = BigInteger.valueOf(1701) // 3.00 cm
    }

    private fun run(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        size: Int = BODY_SIZE,
    ) {
        val run = paragraph.createRun()
        run.fontFamily = FONT
        run.fontSize = size
        run.isBold = bold
        run.isItalic = italic
        if (underline) run.underline = UnderlinePatterns.SINGLE
        run.setText(text)
    }

    private fun paragraph(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        run(p, text)
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph()
    }

    private fun pilierHeading(
        document: XWPFDocument,
        text: String,
        centered: Boolean,
    ) {
        val p = document.createParagraph()
        p.alignment = if (centered) ParagraphAlignment.CENTER else ParagraphAlignment.BOTH
        p.spacingBefore = 240
        p.spacingAfter = 160
        run(p, text, bold = true, underline = true, size = HEADING_SIZE)
    }

    private fun sectionHeading(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.alignment = ParagraphAlignment.BOTH
        p.spacingBefore = 200
        p.spacingAfter = 120
        run(p, text, bold = true, underline = true)
    }

    private fun commentaire(
        document: XWPFDocument,
        text: String?,
    ) {
        if (text.isNullOrBlank()) return
        val label = document.createParagraph()
        label.alignment = ParagraphAlignment.BOTH
        label.spacingBefore = 120
        run(label, "Commentaire :", bold = true, italic = true)
        narrative(document, text)
    }

    /** A bordered data table: bold header row, one row per entry, Tahoma throughout. */
    private fun dataTable(
        document: XWPFDocument,
        headers: List<String>,
        rows: List<List<String>>,
        fontSize: Int = BODY_SIZE,
    ) {
        val table = document.createTable(rows.size + 1, headers.size)
        table.setWidth("100%")
        headers.forEachIndexed { index, header -> setCell(table.getRow(0).getCell(index), header, bold = true, size = fontSize) }
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, value ->
                setCell(table.getRow(rowIndex + 1).getCell(colIndex), value, size = fontSize)
            }
        }
        spacer(document)
    }

    private fun setCell(
        cell: XWPFTableCell,
        text: String,
        bold: Boolean = false,
        size: Int = BODY_SIZE,
    ) {
        val p = cell.paragraphs.first()
        run(p, text, bold = bold, size = size)
    }

    private fun cellTitle(
        cell: XWPFTableCell,
        text: String,
    ) {
        val p =
            if (cell.paragraphs
                    .first()
                    .runs
                    .isEmpty()
            ) {
                cell.paragraphs.first()
            } else {
                cell.addParagraph()
            }
        run(p, text, bold = true)
    }

    private fun cellText(
        cell: XWPFTableCell,
        text: String,
    ) {
        val p =
            if (cell.paragraphs
                    .first()
                    .runs
                    .isEmpty()
            ) {
                cell.paragraphs.first()
            } else {
                cell.addParagraph()
            }
        run(p, text)
    }

    private fun cellLine(
        cell: XWPFTableCell,
        label: String,
        value: String?,
    ) = cellText(cell, "$label : ${value.orRas()}")

    private fun cellMultiline(
        cell: XWPFTableCell,
        text: String,
    ) = text.lines().forEach { cellText(cell, it) }

    /** Merges a row's two cells into one full-width cell (content goes into the first). */
    private fun mergeRow(
        table: XWPFTable,
        rowIndex: Int,
    ) {
        val row = table.getRow(rowIndex)
        val first = row.getCell(0).ctTc
        (first.tcPr ?: first.addNewTcPr()).addNewHMerge().`val` = STMerge.RESTART
        val second = row.getCell(1).ctTc
        (second.tcPr ?: second.addNewTcPr()).addNewHMerge().`val` = STMerge.CONTINUE
    }

    private fun borderless(table: XWPFTable) {
        table.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
        table.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "auto")
    }

    /** Embeds a figure at the page's content width (capped), preserving its aspect ratio. */
    private fun embedImage(
        document: XWPFDocument,
        contentType: String,
        name: String,
        bytes: ByteArray,
    ) {
        try {
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return
            var widthEmu = Units.pixelToEMU(image.width)
            var heightEmu = Units.pixelToEMU(image.height)
            if (widthEmu > MAX_IMAGE_WIDTH_EMU) {
                heightEmu = (heightEmu.toLong() * MAX_IMAGE_WIDTH_EMU / widthEmu).toInt()
                widthEmu = MAX_IMAGE_WIDTH_EMU
            }
            val p = document.createParagraph()
            p.alignment = ParagraphAlignment.CENTER
            ByteArrayInputStream(bytes).use { stream ->
                p.createRun().addPicture(stream, pictureType(contentType), name, widthEmu, heightEmu)
            }
        } catch (_: Exception) {
            // A figure that cannot be decoded/embedded must not break the export.
        }
    }

    private fun pictureType(contentType: String): Int =
        when (contentType.lowercase()) {
            "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG
            "image/gif" -> Document.PICTURE_TYPE_GIF
            "image/bmp" -> Document.PICTURE_TYPE_BMP
            else -> Document.PICTURE_TYPE_PNG
        }

    // ---- Content parsing --------------------------------------------------------------

    private fun tableContent(json: String?): TableContent {
        if (json.isNullOrBlank()) return TableContent()
        // Legacy shape of 1.6 personnes clés: a bare JSON array of rows.
        if (json.trimStart().startsWith("[")) {
            val rows = runCatching { objectMapper.readValue<List<Map<String, Any?>>>(json) }.getOrDefault(emptyList())
            return TableContent(rows = rows)
        }
        return runCatching { objectMapper.readValue<TableContent>(json) }.getOrDefault(TableContent())
    }

    private fun keyValues(json: String?): Map<String, String?> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching { objectMapper.readValue<Map<String, Any?>>(json) }
            .getOrDefault(emptyMap())
            .mapValues { (_, value) -> value?.toString()?.takeIf { it.isNotBlank() } }
    }

    private fun fraisDiversItems(json: String?): List<FraisDiversItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { objectMapper.readValue<List<FraisDiversItem>>(json) }.getOrDefault(emptyList())
    }

    // ---- Value formatting -------------------------------------------------------------

    private fun String?.orRas(): String = this?.takeIf { it.isNotBlank() } ?: "RAS"

    private fun Any?.str(): String = this?.toString().orEmpty()

    private fun fmt(value: LocalDate?): String? = value?.format(dateFormat)

    private fun pct(value: BigDecimal?): String = value?.let { "${it.stripTrailingZeros().toPlainString()} %" } ?: "RAS"

    private fun fee(
        amount: BigDecimal,
        rate: BigDecimal?,
    ): BigDecimal? = rate?.multiply(amount)?.divide(BigDecimal(100), 0, RoundingMode.HALF_UP)

    private fun grouped(amount: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).format(amount.toBigInteger())
    }

    private data class TableContent(
        val narrative: String? = null,
        val rows: List<Map<String, Any?>> = emptyList(),
        val commentaire: String? = null,
    )

    private data class FlexTableContent(
        val narrative: String? = null,
        val columns: List<String> = emptyList(),
        val rows: List<List<String?>> = emptyList(),
        val commentaire: String? = null,
    )

    private data class FinancialLine(
        val label: String? = null,
        val values: List<String?> = emptyList(),
    )

    private data class FinancialContent(
        val years: List<String> = emptyList(),
        val lines: List<FinancialLine> = emptyList(),
        val commentaire: String? = null,
    )

    private data class ImageContent(
        val narrative: String? = null,
        val commentaire: String? = null,
    )

    private data class FraisDiversItem(
        val label: String,
        val montant: BigDecimal,
    )

    private companion object {
        const val FONT = "Tahoma"
        const val BODY_SIZE = 11
        const val HEADING_SIZE = 12

        // 21.0 − 2.50 − 1.75 = 16.75 cm of usable width, in EMU (360 000/cm).
        const val MAX_IMAGE_WIDTH_EMU = (16.75 * 360000).toInt()
    }
}
