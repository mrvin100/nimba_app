package com.nimba.amortizationschedule

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AmortizationScheduleModelTest(
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val moduleApi: AmortizationScheduleModuleApi,
) {
    private fun line(
        numero: String,
        dateEcheance: LocalDate? = LocalDate.of(2026, 5, 1),
        loyerHt: BigDecimal = BigDecimal("534270486.0000"),
        interet: BigDecimal = BigDecimal("29447980.0000"),
    ) = AmortizationScheduleLine(
        numeroEcheance = numero,
        dateEcheance = dateEcheance,
        interet = interet,
        equipement = BigDecimal("460000000.0000"),
        assurance = BigDecimal("34862507.0000"),
        tracking = BigDecimal("7960000.0000"),
        immatriculation = BigDecimal("2000000.0000"),
        capital = BigDecimal("504822507.0000"),
        loyerHt = loyerHt,
        taxes = BigDecimal("5300636.0000"),
        loyerTtc = BigDecimal("539571123.0000"),
        capitalRestantDu = BigDecimal("2019290028.0000"),
    )

    private fun schedule(
        caseId: UUID,
        version: Int,
    ) = AmortizationSchedule(
        creditCaseId = caseId,
        versionNumber = version,
        originalFilename = "echeancier.csv",
        uploadedBy = UUID.randomUUID(),
    ).apply { addLine(line("1")) }

    @Test
    fun `persists a schedule with its lines and reports no active trades yet`() {
        val caseId = UUID.randomUUID()

        val saved = schedules.saveAndFlush(schedule(caseId, 1))

        assertEquals(1, saved.lines.size)
        assertFalse(moduleApi.hasActiveTradesForCase(caseId))
    }

    @Test
    fun `rejects a duplicate version number for the same case`() {
        val caseId = UUID.randomUUID()
        schedules.saveAndFlush(schedule(caseId, 1))

        assertFailsWith<DataIntegrityViolationException> {
            schedules.saveAndFlush(schedule(caseId, 1))
        }
    }

    @Test
    fun `reports schedule presence and a TA summary once uploaded`() {
        val caseId = UUID.randomUUID()
        assertFalse(moduleApi.hasScheduleForCase(caseId))
        assertNull(moduleApi.scheduleSummary(caseId))

        schedules.saveAndFlush(schedule(caseId, 1))

        assertTrue(moduleApi.hasScheduleForCase(caseId))
        val summary = moduleApi.scheduleSummary(caseId)
        assertEquals(BigDecimal("504822507.0000"), summary?.loanAmount)
        assertEquals(1, summary?.durationMonths)
        assertEquals(LocalDate.of(2026, 5, 1), summary?.startDate)
        assertEquals(LocalDate.of(2026, 5, 1), summary?.endDate)
        assertEquals(BigDecimal("460000000.0000"), summary?.totalEquipement)
        assertEquals(BigDecimal("34862507.0000"), summary?.totalAssurance)
        assertEquals(BigDecimal("7960000.0000"), summary?.totalTracking)
        assertEquals(BigDecimal("2000000.0000"), summary?.totalImmatriculation)
        assertEquals(BigDecimal("539571123.0000"), summary?.premierLoyerTtc)
        // Only one ordinary line: loyer mensuel falls back to it, no VR line uploaded.
        assertEquals(BigDecimal("534270486.0000"), summary?.loyerMensuelHt)
        assertNull(summary?.valeurResiduelle)
    }

    @Test
    fun `derives the articulation breakdown across ordinary lines and reads the VR from its own line`() {
        val caseId = UUID.randomUUID()
        val schedule =
            AmortizationSchedule(
                creditCaseId = caseId,
                versionNumber = 1,
                originalFilename = "echeancier.csv",
                uploadedBy = UUID.randomUUID(),
            ).apply {
                addLine(line("1", dateEcheance = LocalDate.of(2026, 5, 1), loyerHt = BigDecimal("600000000.0000")))
                addLine(line("2", dateEcheance = LocalDate.of(2026, 6, 1), loyerHt = BigDecimal("534270486.0000")))
                addLine(
                    line(
                        "VR",
                        dateEcheance = null,
                        loyerHt = BigDecimal.ZERO,
                        interet = BigDecimal("46000000.0000"),
                    ),
                )
            }
        schedules.saveAndFlush(schedule)

        val summary = requireNotNull(moduleApi.scheduleSummary(caseId))

        assertEquals(2, summary.durationMonths)
        assertEquals(BigDecimal("920000000.0000"), summary.totalEquipement)
        // The first line's loyer HT differs (600M); the second, steady-state one is what "loyer mensuel" reports.
        assertEquals(BigDecimal("534270486.0000"), summary.loyerMensuelHt)
        assertEquals(BigDecimal("46000000.0000"), summary.valeurResiduelle)
    }
}
