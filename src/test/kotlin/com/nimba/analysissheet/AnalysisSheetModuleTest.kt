package com.nimba.analysissheet

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.FaVariant
import com.nimba.creditcase.ProductType
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AnalysisSheetModuleTest(
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val sheets: AnalysisSheetModuleApi,
    @Autowired private val users: UserRepository,
) {
    private fun analystId(): UUID =
        requireNotNull(
            users
                .saveAndFlush(
                    User(fullName = "Analyste FA", email = "fa-${UUID.randomUUID()}@banque.test", passwordHash = "hash"),
                ).id,
        )

    private fun leasingCaseId(analyst: UUID): UUID =
        creditCases
            .createCase(
                CreateCreditCaseCommand("Client FA", ProductType.LEASING, "GNF", analyst, contractType = ContractType.AVEC_CONTRAT),
            ).id

    private fun uploadSchedule(caseId: UUID) {
        val line =
            AmortizationScheduleLine(
                numeroEcheance = "1",
                dateEcheance = LocalDate.of(2026, 5, 1),
                interet = BigDecimal("100.0000"),
                equipement = BigDecimal("900.0000"),
                assurance = BigDecimal.ZERO,
                tracking = BigDecimal.ZERO,
                immatriculation = BigDecimal.ZERO,
                capital = BigDecimal("900.0000"),
                loyerHt = BigDecimal("1000.0000"),
                taxes = BigDecimal.ZERO,
                loyerTtc = BigDecimal("1000.0000"),
                capitalRestantDu = BigDecimal.ZERO,
            )
        val schedule =
            AmortizationSchedule(
                creditCaseId = caseId,
                versionNumber = 1,
                originalFilename = "echeancier.csv",
                uploadedBy = UUID.randomUUID(),
            ).apply { addLine(line) }
        schedules.saveAndFlush(schedule)
    }

    @Test
    fun `initiating the FA before the TA is uploaded is rejected`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)

        assertFailsWith<ResponseStatusException> {
            sheets.create(CreateAnalysisSheetCommand(caseId, analyst))
        }
    }

    @Test
    fun `initiates the FA in draft with the variant resolved from the case type`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)
        uploadSchedule(caseId)

        val created = sheets.create(CreateAnalysisSheetCommand(caseId, analyst))

        assertEquals(AnalysisSheetStatus.DRAFT, created.status)
        assertEquals(FaVariant.LEASING_AVEC_CONTRAT, created.faVariant)
        assertEquals(caseId, sheets.findByCase(caseId)?.creditCaseId)
    }

    @Test
    fun `a second FA cannot be initiated for the same case`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)
        uploadSchedule(caseId)
        sheets.create(CreateAnalysisSheetCommand(caseId, analyst))

        assertFailsWith<ResponseStatusException> {
            sheets.create(CreateAnalysisSheetCommand(caseId, analyst))
        }
    }

    @Test
    fun `a section's content can be edited, then locked by publishing`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)
        uploadSchedule(caseId)
        sheets.create(CreateAnalysisSheetCommand(caseId, analyst))

        sheets.updateSection(caseId, FaSectionKey.COVER_PROPOSITION, "Analyse en cours de rédaction")
        val section = sheets.sections(caseId).first { it.key == FaSectionKey.COVER_PROPOSITION }
        assertEquals("Analyse en cours de rédaction", section.contentJson)

        val published = sheets.publish(caseId)
        assertEquals(AnalysisSheetStatus.PUBLISHED, published.status)

        assertFailsWith<ResponseStatusException> { sheets.updateSection(caseId, FaSectionKey.COVER_PROPOSITION, "trop tard") }
        assertFailsWith<ResponseStatusException> { sheets.publish(caseId) }
    }

    @Test
    fun `every registered section is returned even before anything is saved`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)
        uploadSchedule(caseId)
        sheets.create(CreateAnalysisSheetCommand(caseId, analyst))

        val sections = sheets.sections(caseId)

        assertEquals(FaSectionRegistry.sectionsFor(FaVariant.LEASING_AVEC_CONTRAT).size, sections.size)
        assertTrue(sections.all { it.contentJson == null })
    }

    @Test
    fun `rejects saving content to a non-editable or out-of-variant section`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)
        uploadSchedule(caseId)
        sheets.create(CreateAnalysisSheetCommand(caseId, analyst))

        assertFailsWith<ResponseStatusException> {
            sheets.updateSection(caseId, FaSectionKey.PILIER3_RENTABILITE_BANQUE, "non éditable")
        }
        // The payer synthesis belongs to SANS_CONTRAT only — an AVEC_CONTRAT
        // dossier can never write it.
        assertFailsWith<ResponseStatusException> {
            sheets.updateSection(caseId, FaSectionKey.PILIER1_SYNTHESE_PAYEUR, "hors variante")
        }
    }

    @Test
    fun `the risk matrix section is served with its leasing default prefill`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)
        uploadSchedule(caseId)
        sheets.create(CreateAnalysisSheetCommand(caseId, analyst))

        val risques = sheets.sections(caseId).first { it.key == FaSectionKey.PILIER4_RISQUES }

        assertNull(risques.contentJson)
        assertTrue(requireNotNull(risques.defaultContentJson).contains("Risque de crédit"))
    }

    @Test
    fun `deleting the case purges its FA`() {
        val analyst = analystId()
        val caseId = leasingCaseId(analyst)
        uploadSchedule(caseId)
        sheets.create(CreateAnalysisSheetCommand(caseId, analyst))

        creditCases.delete(caseId)

        assertNull(sheets.findByCase(caseId))
    }
}
