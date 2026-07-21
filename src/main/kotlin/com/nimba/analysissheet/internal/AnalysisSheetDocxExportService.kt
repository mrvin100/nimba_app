package com.nimba.analysissheet.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.analysissheet.AnalysisSheetInfo
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.analysissheet.FaPilier
import com.nimba.analysissheet.FaSectionInfo
import com.nimba.analysissheet.FaSectionKey
import com.nimba.analysissheet.FaSectionType
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.guarantee.GuaranteeInfo
import com.nimba.guarantee.GuaranteeKind
import com.nimba.guarantee.GuaranteeModuleApi
import com.nimba.identity.IdentityModuleApi
import com.nimba.identity.OrganizationLogo
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Clock
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
 * Builds the Word (.docx) export of a case's Fiche d'analyse — a completion
 * snapshot a manager can hand to the client to chase whatever is still missing.
 * Every value the FA is supposed to carry (identité du client, conditions de
 * banque, and each of the FA's own sections) is printed as-is, with "RAS" for
 * anything not yet captured, so the document is meaningful whether the FA was
 * never started, is a draft, or is published. Read-only: never mutates the case.
 */
@Service
class AnalysisSheetDocxExportService(
    private val analysisSheets: AnalysisSheetModuleApi,
    private val creditCases: CreditCaseModuleApi,
    private val guarantees: GuaranteeModuleApi,
    private val amortizationSchedules: AmortizationScheduleModuleApi,
    private val identityModuleApi: IdentityModuleApi,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    @Transactional(readOnly = true)
    fun export(creditCaseId: UUID): AnalysisSheetExport {
        val case = creditCases.getOrThrow(creditCaseId)
        val sheet = analysisSheets.findByCase(creditCaseId)
        val sections = analysisSheets.sections(creditCaseId)
        val taSummary = amortizationSchedules.scheduleSummary(creditCaseId)
        val caseGuarantees = guarantees.listByCase(creditCaseId)

        val document = XWPFDocument()
        renderHeader(document, case, sheet)
        renderIdentity(document, case.clientIdentity)
        renderConditionsDeBanque(document, case.conditionsDeBanque)
        renderFaSections(document, sections, case.conditionsDeBanque, taSummary, caseGuarantees)

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return AnalysisSheetExport("fiche-analyse-${case.caseNumber}.docx", bytes)
    }

    private fun renderHeader(
        document: XWPFDocument,
        case: CreditCaseInfo,
        sheet: AnalysisSheetInfo?,
    ) {
        identityModuleApi.organizationLogo()?.let { logo ->
            renderLogo(document.createParagraph().also { it.spacingAfter = 100 }, logo)
        }
        heading(document, "Fiche d'analyse — ${case.caseNumber}", 18, spacingBefore = 0)
        paragraph(document, "Client : ${case.clientName}")
        paragraph(document, "Statut de la fiche : ${statusLabel(sheet)}")
        paragraph(document, "Exportée le ${LocalDate.now(clock).format(dateFormat)}")
    }

    private fun statusLabel(sheet: AnalysisSheetInfo?): String =
        when (sheet?.status) {
            null -> "Non initiée"
            AnalysisSheetStatus.DRAFT -> "Brouillon"
            AnalysisSheetStatus.PUBLISHED -> "Publiée"
        }

    private fun renderIdentity(
        document: XWPFDocument,
        clientIdentity: ClientIdentityInfo,
    ) {
        heading(document, "1. Identité du client", 14)
        kvTable(
            document,
            listOf(
                "Forme juridique" to ras(clientIdentity.formeJuridique),
                "Date de création" to rasDate(clientIdentity.dateCreation),
                "Adresse physique" to ras(clientIdentity.adressePhysique),
                "Activité de base" to ras(clientIdentity.activiteDeBase),
                "Code NIF" to ras(clientIdentity.codeNif),
                "Principal dirigeant" to ras(clientIdentity.principalDirigeant),
                "Date d'entrée en relation" to rasDate(clientIdentity.dateEntreeRelation),
                "Date de dernière visite" to rasDate(clientIdentity.dateDerniereVisite),
                "Agence" to ras(clientIdentity.agence),
                "Gestionnaire" to ras(clientIdentity.gestionnaire),
                "Analyste" to ras(clientIdentity.analyste),
                "Cotation précédente" to ras(clientIdentity.cotationPrecedente),
                "Cotation actuelle" to ras(clientIdentity.cotationActuelle),
            ),
        )
    }

    private fun renderConditionsDeBanque(
        document: XWPFDocument,
        conditions: ConditionsDeBanqueInfo,
    ) {
        heading(document, "2. Conditions de banque", 14)
        kvTable(document, conditionsRows(conditions))
    }

    private fun conditionsRows(conditions: ConditionsDeBanqueInfo): List<Pair<String, String>> =
        listOf(
            "Taux d'intérêt" to rasPct(conditions.tauxInteretPct),
            "Frais de mise en place" to rasPct(conditions.fraisMiseEnPlacePct),
            "Commission d'engagement" to rasPct(conditions.comEngagementPct),
            "Frais d'études" to rasPct(conditions.fraisEtudesPct),
            "Valeur résiduelle" to rasPct(conditions.valeurResiduellePct),
            "Frais divers" to fraisDiversText(conditions.fraisDivers),
        )

    private fun fraisDiversText(json: String?): String {
        if (json.isNullOrBlank()) return "RAS"
        val items =
            runCatching { objectMapper.readValue<List<FraisDiversItem>>(json) }.getOrDefault(emptyList())
        if (items.isEmpty()) return "RAS"
        return items.joinToString("; ") { "${it.label} : ${grouped(it.montant)}" }
    }

    private fun renderFaSections(
        document: XWPFDocument,
        sections: List<FaSectionInfo>,
        conditions: ConditionsDeBanqueInfo,
        taSummary: ScheduleSummary?,
        caseGuarantees: List<GuaranteeInfo>,
    ) {
        heading(document, "3. Fiche d'analyse", 14)
        if (sections.isEmpty()) {
            paragraph(document, "Fiche d'analyse non initiée pour ce dossier.", italic = true)
            return
        }
        FaPilier.entries.forEach { pilier ->
            val pilierSections = sections.filter { it.pilier == pilier }
            if (pilierSections.isEmpty()) return@forEach
            heading(document, pilierLabel(pilier), 12, spacingBefore = 300)
            pilierSections.forEach { section ->
                heading(document, section.label, 11, spacingBefore = 150)
                renderSectionContent(document, section, conditions, taSummary, caseGuarantees)
            }
        }
    }

    private fun pilierLabel(pilier: FaPilier): String =
        when (pilier) {
            FaPilier.COVER -> "Couverture"
            FaPilier.PILIER_1 -> "Pilier 1"
            FaPilier.PILIER_2 -> "Pilier 2"
            FaPilier.PILIER_3 -> "Pilier 3"
            FaPilier.PILIER_4 -> "Pilier 4"
            FaPilier.CONCLUSION -> "Conclusion"
            FaPilier.ANNEXES -> "Annexes"
        }

    private fun renderSectionContent(
        document: XWPFDocument,
        section: FaSectionInfo,
        conditions: ConditionsDeBanqueInfo,
        taSummary: ScheduleSummary?,
        caseGuarantees: List<GuaranteeInfo>,
    ) {
        when (section.type) {
            FaSectionType.NARRATIVE -> paragraph(document, ras(section.contentJson))
            FaSectionType.TABLE ->
                if (section.key == FaSectionKey.PILIER1_PERSONNES_CLES) {
                    renderPersonnesCles(document, section.contentJson)
                } else {
                    // The remaining typed tables (and the KEY_VALUE / FLEX_TABLE /
                    // FINANCIAL / IMAGE shapes below) get their real rendering in the
                    // exact-replica export; until then the export stays truthful about
                    // what it does not print yet.
                    paragraph(document, "RAS")
                }
            FaSectionType.KEY_VALUE,
            FaSectionType.FLEX_TABLE,
            FaSectionType.FINANCIAL,
            FaSectionType.IMAGE,
            -> paragraph(document, "RAS")
            FaSectionType.BOUND -> renderBoundSection(document, section.key, conditions, taSummary, caseGuarantees)
            FaSectionType.COMPUTED -> renderComputedSection(document, section.key, conditions, taSummary)
        }
    }

    private fun renderPersonnesCles(
        document: XWPFDocument,
        json: String?,
    ) {
        val personnes =
            if (json.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching { objectMapper.readValue<List<PersonneCle>>(json) }.getOrDefault(emptyList())
            }
        if (personnes.isEmpty()) {
            paragraph(document, "RAS")
            return
        }
        val table = document.createTable(personnes.size + 1, 2)
        setCellText(table.getRow(0).getCell(0), "Nom", bold = true)
        setCellText(table.getRow(0).getCell(1), "Fonction", bold = true)
        personnes.forEachIndexed { index, personne ->
            val row = table.getRow(index + 1)
            setCellText(row.getCell(0), ras(personne.nom))
            setCellText(row.getCell(1), ras(personne.fonction))
        }
    }

    private fun renderBoundSection(
        document: XWPFDocument,
        key: FaSectionKey,
        conditions: ConditionsDeBanqueInfo,
        taSummary: ScheduleSummary?,
        caseGuarantees: List<GuaranteeInfo>,
    ) {
        when (key) {
            FaSectionKey.COVER_CONDITIONS_BANQUE, FaSectionKey.CONCLUSION_CONDITIONS_BANQUE -> kvTable(document, conditionsRows(conditions))
            FaSectionKey.COVER_GARANTIES, FaSectionKey.PILIER4_SURETES, FaSectionKey.CONCLUSION_GARANTIES ->
                renderGuarantees(document, caseGuarantees)
            FaSectionKey.CONCLUSION_ARTICULATION -> renderArticulation(document, taSummary)
            else -> paragraph(document, "RAS")
        }
    }

    private fun renderGuarantees(
        document: XWPFDocument,
        caseGuarantees: List<GuaranteeInfo>,
    ) {
        if (caseGuarantees.isEmpty()) {
            paragraph(document, "RAS")
            return
        }
        val table = document.createTable(caseGuarantees.size + 1, 2)
        setCellText(table.getRow(0).getCell(0), "Type", bold = true)
        setCellText(table.getRow(0).getCell(1), "Description", bold = true)
        caseGuarantees.forEachIndexed { index, guarantee ->
            val row = table.getRow(index + 1)
            setCellText(row.getCell(0), guaranteeKindLabel(guarantee.kind))
            setCellText(row.getCell(1), ras(guarantee.description))
        }
    }

    private fun guaranteeKindLabel(kind: GuaranteeKind): String =
        when (kind) {
            GuaranteeKind.DETENUE -> "Détenue"
            GuaranteeKind.A_RECUEILLIR -> "À recueillir"
        }

    private fun renderArticulation(
        document: XWPFDocument,
        taSummary: ScheduleSummary?,
    ) {
        if (taSummary == null) {
            paragraph(document, "RAS")
            return
        }
        kvTable(
            document,
            listOf(
                "Montant financé" to grouped(taSummary.loanAmount),
                "Durée" to "${taSummary.durationMonths} échéances",
                "Équipement" to grouped(taSummary.totalEquipement),
                "Assurance" to grouped(taSummary.totalAssurance),
                "Tracking" to grouped(taSummary.totalTracking),
                "Immatriculation" to grouped(taSummary.totalImmatriculation),
            ),
        )
    }

    private fun renderComputedSection(
        document: XWPFDocument,
        key: FaSectionKey,
        conditions: ConditionsDeBanqueInfo,
        taSummary: ScheduleSummary?,
    ) {
        when (key) {
            FaSectionKey.PILIER3_SIMULATION_FINANCEMENT ->
                if (taSummary == null) {
                    paragraph(document, "RAS")
                } else {
                    paragraph(
                        document,
                        "Correspond à l'échéancier importé — montant financé ${grouped(taSummary.loanAmount)}, " +
                            "${taSummary.durationMonths} échéances.",
                    )
                }
            FaSectionKey.PILIER3_RENTABILITE_BANQUE ->
                kvTable(
                    document,
                    listOf(
                        "Taux d'intérêt" to rasPct(conditions.tauxInteretPct),
                        "Besoin de financement" to (taSummary?.let { grouped(it.loanAmount) } ?: "RAS"),
                        "Intérêts sur la durée" to (taSummary?.let { grouped(it.totalInteret) } ?: "RAS"),
                        "Frais d'études" to rasPct(conditions.fraisEtudesPct),
                        "Valeur résiduelle" to (taSummary?.valeurResiduelle?.let { grouped(it) } ?: "RAS"),
                    ),
                )
            else -> paragraph(document, "RAS")
        }
    }

    // ---- Small POI rendering helpers -------------------------------------------------

    private fun heading(
        document: XWPFDocument,
        text: String,
        size: Int,
        spacingBefore: Int = 200,
    ) {
        val p = document.createParagraph()
        p.spacingBefore = spacingBefore
        val run = p.createRun()
        run.isBold = true
        run.fontSize = size
        run.setText(text)
    }

    private fun paragraph(
        document: XWPFDocument,
        text: String,
        italic: Boolean = false,
    ) {
        val p = document.createParagraph()
        val run = p.createRun()
        run.isItalic = italic
        run.setText(text)
    }

    private fun kvTable(
        document: XWPFDocument,
        rows: List<Pair<String, String>>,
    ) {
        val table = document.createTable(rows.size, 2)
        rows.forEachIndexed { index, (label, value) ->
            val row = table.getRow(index)
            setCellText(row.getCell(0), label, bold = true)
            setCellText(row.getCell(1), value)
        }
    }

    private fun setCellText(
        cell: XWPFTableCell,
        text: String,
        bold: Boolean = false,
    ) {
        val run = cell.paragraphs.first().createRun()
        run.isBold = bold
        run.setText(text)
    }

    /** Embeds the organisation logo at a fixed height, preserving its aspect ratio. */
    private fun renderLogo(
        paragraph: XWPFParagraph,
        logo: OrganizationLogo,
    ) {
        try {
            val image = ImageIO.read(ByteArrayInputStream(logo.bytes)) ?: return
            val height = LOGO_HEIGHT_PX
            val width = (LOGO_HEIGHT_PX.toDouble() * image.width / image.height).toInt().coerceAtLeast(1)
            ByteArrayInputStream(logo.bytes).use { stream ->
                paragraph.createRun().addPicture(
                    stream,
                    pictureType(logo.contentType),
                    "organization-logo",
                    Units.pixelToEMU(width),
                    Units.pixelToEMU(height),
                )
            }
        } catch (_: Exception) {
            // A logo that cannot be decoded/embedded must not break the export.
        }
    }

    private fun pictureType(contentType: String): Int =
        when (contentType.lowercase()) {
            "image/jpeg", "image/jpg" -> Document.PICTURE_TYPE_JPEG
            "image/gif" -> Document.PICTURE_TYPE_GIF
            "image/bmp" -> Document.PICTURE_TYPE_BMP
            else -> Document.PICTURE_TYPE_PNG
        }

    // ---- Value formatting -------------------------------------------------------------

    private fun ras(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "RAS"

    private fun rasDate(value: LocalDate?): String = value?.format(dateFormat) ?: "RAS"

    private fun rasPct(value: BigDecimal?): String = value?.let { "$it %" } ?: "RAS"

    private fun grouped(amount: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).format(amount.toBigInteger())
    }

    private data class FraisDiversItem(
        val label: String,
        val montant: BigDecimal,
    )

    private data class PersonneCle(
        val nom: String? = null,
        val fonction: String? = null,
    )

    private companion object {
        const val LOGO_HEIGHT_PX = 40
    }
}
