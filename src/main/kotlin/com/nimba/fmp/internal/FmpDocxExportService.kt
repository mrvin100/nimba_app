package com.nimba.fmp.internal

import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.fmp.FmpInfo
import com.nimba.fmp.FmpModuleApi
import com.nimba.guarantee.GuaranteeKind
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
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
import java.util.Locale
import java.util.UUID

/** One exported FMP document, ready to stream back as a download. */
data class FmpExport(
    val filename: String,
    val content: ByteArray,
)

/**
 * Builds the Word (.docx) export of a case's FMP as an exact replica of the
 * bank's real document (ground truth: `FICHE DE MISE EN PLACE EN OC ET
 * FRERES.docx` in docs/refinements): A4, the template's margins, Tahoma
 * justified body, the unité/client/compte/cotation/GFC identity row, the
 * décision paragraph, the articulation table, the garanties/conditions de
 * banque two-column block, and the DRI/DCM/DRC/DJR/DER/EXCO signature row.
 * An [FmpInfo] always carries everything it needs (it only exists once
 * generated from a finalized PV — see [FmpModuleApi]), so there is no
 * draft/unavailable state to gate here, unlike the PV.
 */
@Service
class FmpDocxExportService(
    private val fmps: FmpModuleApi,
    private val creditCases: CreditCaseModuleApi,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(readOnly = true)
    fun export(creditCaseId: UUID): FmpExport {
        val fmp =
            fmps.findByCase(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche de mise en place pour ce dossier")
        val currency = creditCases.getOrThrow(creditCaseId).currency

        val document = XWPFDocument()
        setUpPage(document)
        repeat(4) { spacer(document) }
        renderTitle(document)
        renderReference(document, fmp)
        renderIdentityRow(document, fmp)
        sectionHeading(document, "DECISION DU COMITE :")
        renderAccordParagraph(document, fmp, currency)
        sectionHeading(document, "ARTICULATION DES FINANCEMENTS")
        renderArticulation(document, fmp.articulation, currency)
        renderGarantiesEtConditions(document, fmp, currency)
        renderSignatureRow(document)

        val bytes =
            ByteArrayOutputStream().use { out ->
                document.write(out)
                document.close()
                out.toByteArray()
            }
        return FmpExport("fmp-${fmp.caseNumber}.docx", bytes)
    }

    private fun renderTitle(document: XWPFDocument) {
        val title = document.createParagraph()
        title.alignment = ParagraphAlignment.CENTER
        title.spacingAfter = 200
        run(title, "FICHE DE MISE EN PLACE EN LOYER", bold = true, size = TITLE_SIZE)
    }

    private fun renderReference(
        document: XWPFDocument,
        fmp: FmpInfo,
    ) {
        val table = document.createTable(1, 2)
        table.setWidth("100%")
        borderless(table)
        setCell(table.getRow(0).getCell(0), "N° Prêt : ${fmp.numeroPret}")
        setCell(table.getRow(0).getCell(1), "Garantie : ${fmp.garantieRef.orRas()}")
        spacer(document)
    }

    private fun renderIdentityRow(
        document: XWPFDocument,
        fmp: FmpInfo,
    ) {
        dataTable(
            document,
            listOf("UNITE", "CLIENT", "N° COMPTE", "COTATION", "GFC EN CHARGE DU DOSSIER"),
            listOf(
                listOf(
                    fmp.identite.agence.orRas(),
                    fmp.clientName,
                    fmp.accountNumber.orRas(),
                    fmp.identite.cotationActuelle.orRas(),
                    fmp.gfcEnCharge,
                ),
            ),
        )
    }

    private fun renderAccordParagraph(
        document: XWPFDocument,
        fmp: FmpInfo,
        currency: String,
    ) {
        val intro = document.createParagraph()
        intro.alignment = ParagraphAlignment.BOTH
        run(
            intro,
            "Le comité marque son accord pour un financement via leasing de $currency ${grouped(fmp.articulation.loanAmount)} " +
                "sur une durée de ${fmp.articulation.durationMonths} mois.",
        )
        spacer(document)
    }

    private fun renderArticulation(
        document: XWPFDocument,
        articulation: ScheduleSummary,
        currency: String,
    ) {
        dataTable(
            document,
            listOf("Type de financement", "Montant", "Durée", "Loyers"),
            listOf(listOf("Leasing", "$currency ${grouped(articulation.loanAmount)}", "${articulation.durationMonths} mois", "Mensuelle")),
        )
    }

    private fun renderGarantiesEtConditions(
        document: XWPFDocument,
        fmp: FmpInfo,
        currency: String,
    ) {
        val table = document.createTable(2, 2)
        table.setWidth("100%")
        setCell(table.getRow(0).getCell(0), "GARANTIES", bold = true)
        setCell(table.getRow(0).getCell(1), "CONDITION DE BANQUE", bold = true)

        val garantiesCell = table.getRow(1).getCell(0)
        cellTitle(garantiesCell, "Garanties Détenues :")
        val detenues = fmp.garanties.filter { it.kind == GuaranteeKind.DETENUE }
        if (detenues.isEmpty()) cellText(garantiesCell, "RAS") else detenues.forEach { cellText(garantiesCell, "${it.description} ;") }
        cellText(garantiesCell, "")
        cellTitle(garantiesCell, "Garanties à recueillir :")
        val aRecueillir = fmp.garanties.filter { it.kind == GuaranteeKind.A_RECUEILLIR }
        if (aRecueillir.isEmpty()) {
            cellText(
                garantiesCell,
                "RAS",
            )
        } else {
            aRecueillir.forEach { cellText(garantiesCell, "${it.description} ;") }
        }

        val conditionsCell = table.getRow(1).getCell(1)
        conditionsLines(fmp.conditionsDeBanque, fmp.articulation.durationMonths, currency).forEach { cellText(conditionsCell, it) }
        spacer(document)
    }

    private fun conditionsLines(
        conditions: ConditionsDeBanqueInfo,
        durationMonths: Int,
        currency: String,
    ): List<String> {
        val lines =
            mutableListOf(
                "Taux : ${pct(conditions.tauxInteretPct)}",
                "Frais de mise en place : ${pct(conditions.fraisMiseEnPlacePct)} HT;",
                "Com d'engagement : ${pct(conditions.comEngagementPct)} HT;",
                "Frais d'études de dossiers : ${pct(conditions.fraisEtudesPct)} HT;",
            )
        fraisDiversItems(conditions.fraisDivers).forEach { lines += "${it.label} : $currency ${grouped(it.montant)}" }
        lines += "Durée du leasing : $durationMonths mois ;"
        lines += "Loyers : Mensuel"
        return lines
    }

    private fun renderSignatureRow(document: XWPFDocument) {
        spacer(document)
        dataTable(document, listOf("DRI", "DCM", "DRC", "DJR", "DER", "EXCO"), listOf(List(6) { "" }))
    }

    // ---- POI helpers (same conventions as the FA / PV exports) --------------------------

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
        size: Int = BODY_SIZE,
    ) {
        val run = paragraph.createRun()
        run.fontFamily = FONT
        run.fontSize = size
        run.isBold = bold
        run.setText(text)
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
        run(p, text, bold = true)
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

    private fun pct(value: BigDecimal?): String = value?.let { "${it.stripTrailingZeros().toPlainString()}%" } ?: "RAS"

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
        const val TITLE_SIZE = 14
    }
}
