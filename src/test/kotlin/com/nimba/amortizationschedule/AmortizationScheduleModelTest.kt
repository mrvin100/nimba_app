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

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class AmortizationScheduleModelTest(
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val moduleApi: AmortizationScheduleModuleApi,
) {
    private fun line(numero: String) =
        AmortizationScheduleLine(
            numeroEcheance = numero,
            dateEcheance = LocalDate.of(2026, 5, 1),
            interet = BigDecimal("29447980.0000"),
            equipement = BigDecimal("460000000.0000"),
            assurance = BigDecimal("34862507.0000"),
            tracking = BigDecimal("7960000.0000"),
            immatriculation = BigDecimal("2000000.0000"),
            capital = BigDecimal("504822507.0000"),
            loyerHt = BigDecimal("534270486.0000"),
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
}
