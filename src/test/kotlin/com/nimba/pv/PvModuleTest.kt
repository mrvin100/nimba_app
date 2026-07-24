package com.nimba.pv

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.analysissheet.FaSectionKey
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.creditcase.UpdateConditionsDeBanqueCommand
import com.nimba.guarantee.CreateGuaranteeCommand
import com.nimba.guarantee.GuaranteeKind
import com.nimba.guarantee.GuaranteeModuleApi
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.seedMember
import com.nimba.workflow.WorkflowAction
import com.nimba.workflow.internal.WorkflowService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class PvModuleTest(
    @Autowired private val pvs: PvModuleApi,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val guarantees: GuaranteeModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val workflow: WorkflowService,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun memberId(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

    /**
     * A dossier driven all the way to APPROUVE. [withIdentityAndConditions] set
     * (the default) also captures an identity, a guarantee and conditions de
     * banque; `false` leaves both entirely blank, to exercise finalizing a PV
     * whose snapshot embeddables would end up with every column null.
     */
    private fun approvedDossier(withIdentityAndConditions: Boolean = true): UUID {
        val dri = memberId("pv-dri-${UUID.randomUUID()}@banque.test", Department.DRI)
        val dcm = memberId("pv-dcm-${UUID.randomUUID()}@banque.test", Department.DCM)
        val drc = memberId("pv-drc-${UUID.randomUUID()}@banque.test", Department.DRC)
        val comite1 = memberId("pv-comite1-${UUID.randomUUID()}@banque.test", Department.COMITE)
        val comite2 = memberId("pv-comite2-${UUID.randomUUID()}@banque.test", Department.COMITE)

        val clientId = com.nimba.seedClient(clients, "Client PV")
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(clientId, ProductType.LEASING, "GNF", dri, contractType = ContractType.AVEC_CONTRAT),
                ).id
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
        schedules.saveAndFlush(AmortizationSchedule(caseId, 1, "echeancier.csv", dri).apply { addLine(line) })
        analysisSheets.create(CreateAnalysisSheetCommand(caseId, dri))
        if (withIdentityAndConditions) {
            clients.update(
                clientId,
                com.nimba.client.UpdateClientCommand(
                    raisonSociale = "Client PV",
                    formeJuridique = "SARL",
                    principalDirigeant = "Mamadou Diallo",
                ),
            )
            creditCases.updateConditionsDeBanque(
                caseId,
                UpdateConditionsDeBanqueCommand(tauxInteretPct = BigDecimal("9.5"), valeurResiduellePct = BigDecimal("2")),
            )
            guarantees.create(CreateGuaranteeCommand(caseId, GuaranteeKind.DETENUE, "Nantissement matériel", dri))
            // Points forts/faibles are written on the FA (§5 conclusion) while it is
            // still a draft — publishing locks section edits, exactly like every
            // other FA section.
            analysisSheets.updateSection(caseId, FaSectionKey.CONCLUSION_POINTS_FORTS, "Client fidèle")
            analysisSheets.updateSection(caseId, FaSectionKey.CONCLUSION_POINTS_FAIBLES, "Trésorerie tendue")
        }
        analysisSheets.publish(caseId)

        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)
        workflow.act(caseId, drc, WorkflowAction.APPROVE, null)
        workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null)
        workflow.act(caseId, comite1, WorkflowAction.APPROVE, null)
        workflow.act(caseId, comite2, WorkflowAction.APPROVE, null)
        return caseId
    }

    @Test
    fun `a PV cannot be created before the comite approves the dossier`() {
        val dri = memberId("pv-early-dri@banque.test", Department.DRI)
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "Trop tôt"),
                        ProductType.LEASING,
                        "GNF",
                        dri,
                        contractType = ContractType.AVEC_CONTRAT,
                    ),
                ).id

        assertFailsWith<ResponseStatusException> {
            pvs.create(CreatePvCommand(caseId, dri, LocalDate.of(2026, 7, 13)))
        }
    }

    @Test
    fun `drafts, edits and finalizes a PV with a frozen snapshot`() {
        val caseId = approvedDossier()
        val dcm = memberId("pv-draft-dcm@banque.test", Department.DCM)

        val created = pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 7, 13)))
        assertEquals(PvStatus.DRAFT, created.status)
        assertNull(created.snapshot)

        val updated =
            pvs.updateDraft(
                caseId,
                UpdatePvDraftCommand(
                    seanceDate = LocalDate.of(2026, 7, 14),
                    rapporteur = "Souwla Soumaoro",
                    president = "Emile Traoré",
                    debats =
                        listOf(
                            PvDebat("Retard sur un précédent crédit", "Retard isolé, régularisé", "Favorable sous condition"),
                        ),
                ),
            )
        assertEquals(LocalDate.of(2026, 7, 14), updated.seanceDate)
        assertEquals(1, updated.debats.size)

        val finalized = pvs.finalize(caseId)
        assertEquals(PvStatus.FINAL, finalized.status)
        val snapshot = requireNotNull(finalized.snapshot)
        assertEquals("SARL", snapshot.identite.formeJuridique)
        assertEquals(BigDecimal("9.500"), snapshot.conditionsDeBanque.tauxInteretPct)
        assertEquals(BigDecimal("2.000"), snapshot.conditionsDeBanque.valeurResiduellePct)
        assertEquals(1, snapshot.garanties.size)
        assertEquals("Nantissement matériel", snapshot.garanties.first().description)
        assertEquals(BigDecimal("900.0000"), snapshot.articulation.loanAmount)
        assertEquals("Client fidèle", snapshot.pointsForts)
        assertEquals("Trésorerie tendue", snapshot.pointsFaibles)

        // The snapshot must survive the dossier's live data changing afterward.
        val clientId = requireNotNull(creditCases.findById(caseId)).clientId
        clients.update(clientId, com.nimba.client.UpdateClientCommand(raisonSociale = "Client PV", formeJuridique = "SA"))
        val reloaded = requireNotNull(pvs.findByCase(caseId))
        assertEquals("SARL", requireNotNull(reloaded.snapshot).identite.formeJuridique)
    }

    @Test
    fun `finalizes a PV even when identity and conditions de banque were never captured`() {
        val caseId = approvedDossier(withIdentityAndConditions = false)
        val dcm = memberId("pv-blank-dcm@banque.test", Department.DCM)
        pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 7, 13)))

        val finalized = pvs.finalize(caseId)

        val snapshot = requireNotNull(finalized.snapshot)
        assertNull(snapshot.identite.formeJuridique)
        assertNull(snapshot.conditionsDeBanque.tauxInteretPct)
        assertEquals(0, snapshot.garanties.size)
        assertEquals(BigDecimal("900.0000"), snapshot.articulation.loanAmount)
        assertNull(snapshot.pointsForts)
        assertNull(snapshot.pointsFaibles)
    }

    @Test
    fun `a second PV cannot be created for the same case`() {
        val caseId = approvedDossier()
        val dcm = memberId("pv-dup-dcm@banque.test", Department.DCM)
        pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 7, 13)))

        assertFailsWith<ResponseStatusException> {
            pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 7, 13)))
        }
    }

    @Test
    fun `a finalized PV rejects further draft edits`() {
        val caseId = approvedDossier()
        val dcm = memberId("pv-locked-dcm@banque.test", Department.DCM)
        pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 7, 13)))
        pvs.finalize(caseId)

        assertFailsWith<ResponseStatusException> {
            pvs.updateDraft(
                caseId,
                UpdatePvDraftCommand(LocalDate.of(2026, 7, 13), null, null, emptyList()),
            )
        }
        assertFailsWith<ResponseStatusException> { pvs.finalize(caseId) }
    }

    @Test
    fun `deleting the case purges its PV`() {
        val caseId = approvedDossier()
        val dcm = memberId("pv-purge-dcm@banque.test", Department.DCM)
        pvs.create(CreatePvCommand(caseId, dcm, LocalDate.of(2026, 7, 13)))
        pvs.finalize(caseId)

        creditCases.delete(caseId)

        assertNull(pvs.findByCase(caseId))
    }
}
