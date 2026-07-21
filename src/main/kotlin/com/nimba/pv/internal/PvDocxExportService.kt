package com.nimba.pv.internal

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.guarantee.GuaranteeKind
import com.nimba.pv.PvDebat
import com.nimba.pv.PvGuaranteeSnapshot
import com.nimba.pv.PvModuleApi
import com.nimba.pv.PvStatus
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.UnderlinePatterns
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import org.apache.poi.xwpf.usermodel.XWPFTableCell
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/** One exported PV document, ready to stream back as a download. */
data class PvExport(
    val filename: String,
    val content: ByteArray,
)

/**
 * Builds the Word (.docx) export of a case's finalized PV as an exact replica
 * of the bank's real document (ground truth: PV_page_1.jpg / PV_page_2.jpg,
 * PV ICAB CONSTRUCTION séance 18/03/2026, in docs/refinements): A4, the
 * template's margins, Tahoma justified body, the identity block, the besoin
 * exprimé (repeated verbatim as the décision — same wording, same table, per
 * the real document), débats table, points forts/faibles, garanties,
 * conditions de banque and the rapporteur/président signature block. Only a
 * FINAL PV has a snapshot to print — export is unavailable on a draft, unlike
 * the FA, which prints RAS for anything not yet captured.
 */
@Service
class PvDocxExportService(
    private val pvs: PvModuleApi,
    private val creditCases: CreditCaseModuleApi,
    private val objectMapper: ObjectMapper,
) {
    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    @Transactional(readOnly = true)
    fun export(creditCaseId: UUID): PvExport {
        val case = creditCases.getOrThrow(creditCaseId)
        val pv =
            pvs.findByCase(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun PV pour ce dossier")
        if (pv.status != PvStatus.FINAL) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Le PV doit être finalisé avant d'être exporté")
        }
        val snapshot = requireNotNull(pv.snapshot) { "A FINAL PV always carries a snapshot" }

        val document = XWPFDocument()
        setUpPage(document)
        repeat(4) { spacer(document) }
        renderTitle(document, pv.seanceDate)
        renderIdentity(document, case, snapshot.identite)
        sectionHeading(document, "BESOIN EXPRIME :")
        renderAccordParagraph(document, case, snapshot.articulation)
        sectionHeading(document, "DEBATS DU COMITE :")
        renderDebats(document, pv.debats)
        renderPointsFortsFaibles(document, snapshot.pointsForts, snapshot.pointsFaibles)
        sectionHeading(document, "DECISION DU COMITE :")
        renderAccordParagraph(document, case, snapshot.articulation)
        renderGaranties(document, snapshot.garanties)
        renderConditions(document, snapshot.conditionsDeBanque, snapshot.articulation.durationMonths)
        renderSignatures(document, pv.rapporteur, pv.president)

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return PvExport("pv-${case.caseNumber}.docx", bytes)
    }

    private fun renderTitle(
        document: XWPFDocument,
        seanceDate: LocalDate,
    ) {
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        run(title, "PROCES VERBAL DE COMITE DE CREDIT", bold = true, size = TITLE_SIZE)
        val subtitle = document.createParagraph()
        subtitle.alignment = ParagraphAlignment.CENTER
        subtitle.spacingAfter = 200
        run(subtitle, "SEANCE DU ${fmt(seanceDate)}", bold = true, size = TITLE_SIZE)
    }

    private fun renderIdentity(
        document: XWPFDocument,
        case: CreditCaseInfo,
        identity: ClientIdentityInfo,
    ) {
        val heading = document.createParagraph()
        heading.spacingAfter = 120
        run(heading, "I. ${case.clientName}", bold = true, size = HEADING_SIZE)

        val pairs =
            listOf(
                "RAISON SOCIALE" to case.clientName,
                "FORME JURIDIQUE" to identity.formeJuridique,
                "DATE DE CREATION" to fmt(identity.dateCreation),
                "ADRESSE PHYSIQUE" to identity.adressePhysique,
                "ACTIVITE DE BASE" to identity.activiteDeBase,
                "N° DE COMPTE" to case.accountNumber,
                "PRINCIPAL DIRIGEANT" to identity.principalDirigeant,
                "DATE ENTREE EN RELATION" to fmt(identity.dateEntreeRelation),
                "GESTIONNAIRE" to identity.gestionnaire,
                "AGENCE" to identity.agence,
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

    /** The "besoin exprimé" paragraph — printed a second time, verbatim, under "décision du comité" (see [export]). */
    private fun renderAccordParagraph(
        document: XWPFDocument,
        case: CreditCaseInfo,
        articulation: ScheduleSummary,
    ) {
        val currency = case.currency
        val intro = document.createParagraph()
        intro.alignment = ParagraphAlignment.BOTH
        run(
            intro,
            "Le comité marque son accord pour un crédit-bail de $currency ${grouped(articulation.loanAmount)} sur une " +
                "durée de ${articulation.durationMonths} mois décomposé comme suit :",
        )
        breakdownLines(currency, articulation).forEach { line ->
            val p = document.createParagraph()
            p.alignment = ParagraphAlignment.BOTH
            p.indentationLeft = 400
            run(p, "➤ $line")
        }
        spacer(document)
    }

    private fun breakdownLines(
        currency: String,
        articulation: ScheduleSummary,
    ): List<String> {
        val lines = mutableListOf("$currency ${grouped(articulation.totalEquipement)} pour l'acquisition des équipements ;")
        if (articulation.totalAssurance.signum() != 0) {
            lines += "$currency ${grouped(articulation.totalAssurance)} pour l'assurance tous risques pendant la période du leasing ;"
        }
        if (articulation.totalTracking.signum() != 0) {
            lines += "$currency ${grouped(articulation.totalTracking)} pour le tracking pendant la période du leasing ;"
        }
        if (articulation.totalImmatriculation.signum() != 0) {
            lines += "$currency ${grouped(articulation.totalImmatriculation)} pour l'immatriculation."
        }
        return lines
    }

    private fun renderDebats(
        document: XWPFDocument,
        debats: List<PvDebat>,
    ) {
        if (debats.isEmpty()) {
            paragraph(document, "RAS")
            return
        }
        dataTable(
            document,
            listOf("PREOCCUPATIONS DU COMITE", "REPONSES DU GESTIONNAIRE", "RECOMMANDATIONS"),
            debats.map { listOf(it.preoccupation, it.reponse, it.recommandation) },
        )
    }

    private fun renderPointsFortsFaibles(
        document: XWPFDocument,
        pointsForts: String?,
        pointsFaibles: String?,
    ) {
        sectionHeading(document, "POINTS FORTS")
        narrative(document, pointsForts)
        sectionHeading(document, "POINTS FAIBLES")
        narrative(document, pointsFaibles)
    }

    private fun renderGaranties(
        document: XWPFDocument,
        garanties: List<PvGuaranteeSnapshot>,
    ) {
        sectionHeading(document, "GARANTIES")
        val detenues = garanties.filter { it.kind == GuaranteeKind.DETENUE }
        val aRecueillir = garanties.filter { it.kind == GuaranteeKind.A_RECUEILLIR }

        val detenuesTitle = document.createParagraph()
        run(detenuesTitle, "Garanties détenues :", bold = true, underline = true)
        if (detenues.isEmpty()) paragraph(document, "RAS") else detenues.forEach { paragraph(document, "${it.description} ;") }
        spacer(document)

        val aRecueillirTitle = document.createParagraph()
        run(aRecueillirTitle, "Garantie à recueillir :", bold = true, underline = true)
        if (aRecueillir.isEmpty()) paragraph(document, "RAS") else aRecueillir.forEach { paragraph(document, "${it.description} ;") }
        spacer(document)
    }

    private fun renderConditions(
        document: XWPFDocument,
        conditions: ConditionsDeBanqueInfo,
        durationMonths: Int,
    ) {
        sectionHeading(document, "CONDITIONS DE BANQUE")
        val lines =
            mutableListOf(
                "Frais de mise en place : ${pct(conditions.fraisMiseEnPlacePct)} HT;",
                "Com d'engagement : ${pct(conditions.comEngagementPct)} HT;",
                "Frais d'études de dossiers : ${pct(conditions.fraisEtudesPct)} HT;",
                "Durée du leasing : $durationMonths mois ;",
                "Valeur résiduelle : ${pct(conditions.valeurResiduellePct)} HT.",
            )
        fraisDiversItems(conditions.fraisDivers).forEach { lines += "${it.label} : ${grouped(it.montant)}" }
        lines.forEach { paragraph(document, it) }
    }

    private fun renderSignatures(
        document: XWPFDocument,
        rapporteur: String?,
        president: String?,
    ) {
        spacer(document)
        val table = document.createTable(2, 2)
        table.setWidth("100%")
        borderless(table)
        setCell(table.getRow(0).getCell(0), "LE RAPPORTEUR", bold = true)
        setCell(table.getRow(0).getCell(1), "LE PRESIDENT", bold = true)
        table.getRow(1).getCell(0).addParagraph()
        table.getRow(1).getCell(1).addParagraph()
        setCell(table.getRow(1).getCell(0), rapporteur.orRas())
        setCell(table.getRow(1).getCell(1), president.orRas())
    }

    // ---- POI helpers (same conventions as the FA export) -------------------------------

    private fun setUpPage(document: XWPFDocument) {
        val sectPr = document.document.body.addNewSectPr()
        val pageSize = sectPr.addNewPgSz()
        pageSize.w = BigInteger.valueOf(11906)
        pageSize.h = BigInteger.valueOf(16838)
        val margins = sectPr.addNewPgMar()
        margins.left = BigInteger.valueOf(1418)
        margins.right = BigInteger.valueOf(992)
        margins.top = BigInteger.valueOf(567)
        margins.bottom = BigInteger.valueOf(1701)
    }

    private fun run(
        paragraph: XWPFParagraph,
        text: String,
        bold: Boolean = false,
        underline: Boolean = false,
        size: Int = BODY_SIZE,
    ) {
        val run = paragraph.createRun()
        run.fontFamily = FONT
        run.fontSize = size
        run.isBold = bold
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

    private fun narrative(
        document: XWPFDocument,
        text: String?,
    ) {
        (text?.takeIf { it.isNotBlank() } ?: "RAS").lines().forEach { line -> paragraph(document, line) }
    }

    private fun spacer(document: XWPFDocument) {
        document.createParagraph()
    }

    private fun sectionHeading(
        document: XWPFDocument,
        text: String,
    ) {
        val p = document.createParagraph()
        p.spacingBefore = 200
        p.spacingAfter = 120
        run(p, text, bold = true, underline = true)
    }

    private fun dataTable(
        document: XWPFDocument,
        headers: List<String>,
        rows: List<List<String>>,
    ) {
        val table = document.createTable(rows.size + 1, headers.size)
        table.setWidth("100%")
        headers.forEachIndexed { index, header -> setCell(table.getRow(0).getCell(index), header, bold = true) }
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, value -> setCell(table.getRow(rowIndex + 1).getCell(colIndex), value) }
        }
        spacer(document)
    }

    private fun setCell(
        cell: XWPFTableCell,
        text: String,
        bold: Boolean = false,
    ) {
        val p = cell.paragraphs.first()
        run(p, text, bold = bold)
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

    private fun fmt(value: LocalDate?): String? = value?.format(dateFormat)

    private fun pct(value: BigDecimal?): String = value?.let { "${it.stripTrailingZeros().toPlainString()} %" } ?: "RAS"

    private fun grouped(amount: BigDecimal): String {
        val symbols = DecimalFormatSymbols(Locale.FRENCH).apply { groupingSeparator = ' ' }
        return DecimalFormat("#,##0", symbols).format(amount.toBigInteger())
    }

    private fun fraisDiversItems(json: String?): List<FraisDiversItem> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { objectMapper.readValue<List<FraisDiversItem>>(json) }.getOrDefault(emptyList())
    }

    private data class FraisDiversItem(
        val label: String,
        val montant: BigDecimal,
    )

    private companion object {
        const val FONT = "Tahoma"
        const val BODY_SIZE = 11
        const val HEADING_SIZE = 13
        const val TITLE_SIZE = 14
    }
}
