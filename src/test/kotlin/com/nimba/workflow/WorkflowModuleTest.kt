package com.nimba.workflow

import com.nimba.TestcontainersConfiguration
import com.nimba.amortizationschedule.internal.AmortizationSchedule
import com.nimba.amortizationschedule.internal.AmortizationScheduleLine
import com.nimba.amortizationschedule.internal.AmortizationScheduleRepository
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.analysissheet.CreateAnalysisSheetCommand
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ProductType
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.notification.internal.NotificationService
import com.nimba.seedMember
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
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class WorkflowModuleTest(
    @Autowired private val workflow: WorkflowService,
    @Autowired private val creditCases: CreditCaseModuleApi,
    @Autowired private val clients: com.nimba.client.ClientModuleApi,
    @Autowired private val analysisSheets: AnalysisSheetModuleApi,
    @Autowired private val schedules: AmortizationScheduleRepository,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val notifications: NotificationService,
) {
    private fun memberId(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

    private fun readyDossier(): UUID {
        val dri = memberId("wf-dri-${UUID.randomUUID()}@banque.test", Department.DRI)
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "Client Workflow"),
                        ProductType.LEASING,
                        "GNF",
                        dri,
                        contractType = ContractType.AVEC_CONTRAT,
                    ),
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
        schedules.saveAndFlush(
            AmortizationSchedule(caseId, 1, "echeancier.csv", dri).apply { addLine(line) },
        )
        analysisSheets.create(CreateAnalysisSheetCommand(caseId, dri))
        analysisSheets.publish(caseId)
        return caseId
    }

    @Test
    fun `a created case starts its lifecycle at BROUILLON`() {
        val dri = memberId("wf-init-dri@banque.test", Department.DRI)
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "Init"),
                        ProductType.LEASING,
                        "GNF",
                        dri,
                        contractType = ContractType.SANS_CONTRAT,
                    ),
                ).id

        assertEquals(WorkflowStatus.BROUILLON, workflow.state(caseId, dri).status)
    }

    @Test
    fun `full happy path DRI submit to two comite approvals`() {
        val caseId = readyDossier()
        val dri = memberId("wf-hp-dri@banque.test", Department.DRI)
        val dcm = memberId("wf-hp-dcm@banque.test", Department.DCM)
        val drc = memberId("wf-hp-drc@banque.test", Department.DRC)
        val comite1 = memberId("wf-hp-comite1@banque.test", Department.COMITE)
        val comite2 = memberId("wf-hp-comite2@banque.test", Department.COMITE)

        assertEquals(WorkflowStatus.EN_REVUE_DCM, workflow.act(caseId, dri, WorkflowAction.SUBMIT, null).status)
        assertEquals(1L, notifications.unreadCount(dcm), "SUBMIT must notify the DCM")

        assertEquals(WorkflowStatus.EN_REVUE_DRC, workflow.act(caseId, dcm, WorkflowAction.APPROVE, "OK crédit").status)
        assertEquals(1L, notifications.unreadCount(drc), "DCM approval must notify the DRC")

        // The DRC's avis favorable goes straight to the DCM verification — the
        // DRC analyses the dossier only once.
        assertEquals(WorkflowStatus.EN_VERIFICATION_DCM, workflow.act(caseId, drc, WorkflowAction.APPROVE, null).status)
        assertEquals(2L, notifications.unreadCount(dcm), "the DRC's avis favorable must notify the DCM")

        // Only the DCM sends the dossier to the comité.
        assertEquals(WorkflowStatus.PRET_POUR_COMITE, workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null).status)
        assertEquals(1L, notifications.unreadCount(comite1), "the envoi comité must notify every comité member")
        assertEquals(1L, notifications.unreadCount(comite2), "the envoi comité must notify every comité member")

        val afterFirst = workflow.act(caseId, comite1, WorkflowAction.APPROVE, null)
        assertEquals(WorkflowStatus.PRET_POUR_COMITE, afterFirst.status)
        assertEquals(1, afterFirst.comiteApprovals)
        assertEquals(1L, notifications.unreadCount(comite2), "a first comité vote (no status change) must not notify again")

        val afterSecond = workflow.act(caseId, comite2, WorkflowAction.APPROVE, null)
        assertEquals(WorkflowStatus.APPROUVE, afterSecond.status)
        assertEquals(2, afterSecond.comiteApprovals)
        assertTrue(afterSecond.timeline.any { it.action == WorkflowAction.SUBMIT })
        // dcm holds SUBMIT + avis favorable notifications (never read in this
        // test); the comité outcome adds a third one.
        assertEquals(3L, notifications.unreadCount(dcm), "the comité outcome must notify the DCM")
        assertEquals(1L, notifications.unreadCount(dri), "the comité outcome must notify the DRI")
    }

    @Test
    fun `DRC observations send the dossier through the corrections lane`() {
        val caseId = readyDossier()
        val dri = memberId("wf-drc-dri@banque.test", Department.DRI)
        val dcm = memberId("wf-drc-dcm@banque.test", Department.DCM)
        val drc = memberId("wf-drc-drc@banque.test", Department.DRC)

        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)

        val returned = workflow.act(caseId, drc, WorkflowAction.REQUEST_CHANGES, "Compléter la logistique")
        assertEquals(WorkflowStatus.CORRECTIONS_DRI, returned.status)
        assertEquals(AnalysisSheetStatus.DRAFT, analysisSheets.findByCase(caseId)?.status, "the FA must reopen for the DRI")
        assertEquals(1L, notifications.unreadCount(dri), "DRC observations must notify the DRI")
        assertEquals(2L, notifications.unreadCount(dcm), "the DCM must receive a copy of the DRC observations")

        // The DRI corrects, republishes and submits the corrections — the
        // dossier goes to the DCM verification, not back through the DRC.
        analysisSheets.publish(caseId)
        assertEquals(
            WorkflowStatus.EN_VERIFICATION_DCM,
            workflow.act(caseId, dri, WorkflowAction.SUBMIT_CORRECTIONS, null).status,
        )
        assertEquals(WorkflowStatus.PRET_POUR_COMITE, workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null).status)
    }

    @Test
    fun `submitting without a published FA is rejected`() {
        val dri = memberId("wf-nofa-dri@banque.test", Department.DRI)
        val caseId =
            creditCases
                .createCase(
                    CreateCreditCaseCommand(
                        com.nimba.seedClient(clients, "Sans FA"),
                        ProductType.LEASING,
                        "GNF",
                        dri,
                        contractType = ContractType.AVEC_CONTRAT,
                    ),
                ).id

        assertFailsWith<ResponseStatusException> { workflow.act(caseId, dri, WorkflowAction.SUBMIT, null) }
    }

    @Test
    fun `a comite return to be completed goes through the corrections lane`() {
        val caseId = readyDossier()
        val dri = memberId("wf-ret-dri@banque.test", Department.DRI)
        val dcm = memberId("wf-ret-dcm@banque.test", Department.DCM)
        val drc = memberId("wf-ret-drc@banque.test", Department.DRC)
        val comite1 = memberId("wf-ret-comite1@banque.test", Department.COMITE)

        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)
        workflow.act(caseId, drc, WorkflowAction.APPROVE, null)
        workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null)
        workflow.act(caseId, comite1, WorkflowAction.APPROVE, null)

        val returned = workflow.act(caseId, comite1, WorkflowAction.REQUEST_COMPLETION, "Compléter les garanties")
        assertEquals(WorkflowStatus.A_COMPLETER, returned.status)
        assertEquals(0, returned.comiteApprovals)
        assertEquals(AnalysisSheetStatus.DRAFT, analysisSheets.findByCase(caseId)?.status)
        assertEquals(1L, notifications.unreadCount(dri), "the return to DRI must notify the DRI")

        // Completion goes back through the DCM verification, then the comité.
        analysisSheets.publish(caseId)
        assertEquals(
            WorkflowStatus.EN_VERIFICATION_DCM,
            workflow.act(caseId, dri, WorkflowAction.SUBMIT_CORRECTIONS, null).status,
        )
    }

    @Test
    fun `a comite rejection requires the DCM's explicit archiving step`() {
        val caseId = readyDossier()
        val dri = memberId("wf-rej-dri@banque.test", Department.DRI)
        val dcm = memberId("wf-rej-dcm@banque.test", Department.DCM)
        val drc = memberId("wf-rej-drc@banque.test", Department.DRC)
        val comite = memberId("wf-rej-comite@banque.test", Department.COMITE)

        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)
        workflow.act(caseId, drc, WorkflowAction.APPROVE, null)
        workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null)

        val rejected = workflow.act(caseId, comite, WorkflowAction.REJECT, "Signature insuffisante")
        assertEquals(WorkflowStatus.EN_ARCHIVAGE, rejected.status)
        assertTrue(creditCases.findById(caseId)?.archivedAt == null, "the rejection alone must not archive yet")

        // The DCM records the final archiving note; only then is the dossier archived.
        val archived = workflow.act(caseId, dcm, WorkflowAction.ARCHIVE, "Archivé suite au rejet du comité")
        assertEquals(WorkflowStatus.REJETE, archived.status)
        assertTrue(creditCases.findById(caseId)?.archivedAt != null)
    }

    @Test
    fun `the DRI can toggle the FA back to draft only before the first submission`() {
        val caseId = readyDossier()
        val dri = memberId("wf-unp-dri@banque.test", Department.DRI)
        val dcm = memberId("wf-unp-dcm@banque.test", Department.DCM)

        // Never submitted: publish ↔ draft toggles freely.
        assertEquals(AnalysisSheetStatus.DRAFT, analysisSheets.unpublish(caseId).status)
        analysisSheets.publish(caseId)

        // Once submitted (then returned), the self-unpublish is gone for good.
        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.REQUEST_CHANGES, "À revoir")
        analysisSheets.publish(caseId)
        assertFailsWith<ResponseStatusException> { analysisSheets.unpublish(caseId) }
    }

    @Test
    fun `a member of the wrong direction cannot act`() {
        val caseId = readyDossier()
        val dri = memberId("wf-wrong-dri@banque.test", Department.DRI)
        val drc = memberId("wf-wrong-drc@banque.test", Department.DRC)
        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)

        // At EN_REVUE_DCM only a DCM member may act; a DRC member is forbidden.
        val error = assertFailsWith<ResponseStatusException> { workflow.act(caseId, drc, WorkflowAction.APPROVE, null) }
        assertEquals(403, error.statusCode.value())
    }

    @Test
    fun `the same comite member cannot approve twice`() {
        val caseId = readyDossier()
        val dri = memberId("wf-dup-dri@banque.test", Department.DRI)
        val dcm = memberId("wf-dup-dcm@banque.test", Department.DCM)
        val drc = memberId("wf-dup-drc@banque.test", Department.DRC)
        val comite = memberId("wf-dup-comite@banque.test", Department.COMITE)

        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)
        workflow.act(caseId, dcm, WorkflowAction.APPROVE, null)
        workflow.act(caseId, drc, WorkflowAction.APPROVE, null)
        workflow.act(caseId, dcm, WorkflowAction.SEND_TO_COMITE, null)
        workflow.act(caseId, comite, WorkflowAction.APPROVE, null)

        assertFailsWith<ResponseStatusException> { workflow.act(caseId, comite, WorkflowAction.APPROVE, null) }
    }

    @Test
    fun `a return action requires a comment`() {
        val caseId = readyDossier()
        val dri = memberId("wf-cmt-dri@banque.test", Department.DRI)
        val dcm = memberId("wf-cmt-dcm@banque.test", Department.DCM)
        workflow.act(caseId, dri, WorkflowAction.SUBMIT, null)

        val error = assertFailsWith<ResponseStatusException> { workflow.act(caseId, dcm, WorkflowAction.REQUEST_CHANGES, "  ") }
        assertEquals(400, error.statusCode.value())
    }
}
