package com.nimba.pv.internal

import com.nimba.amortizationschedule.AmortizationScheduleModuleApi
import com.nimba.amortizationschedule.ScheduleSummary
import com.nimba.analysissheet.AnalysisSheetModuleApi
import com.nimba.analysissheet.FaSectionKey
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ConditionsDeBanqueInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.guarantee.GuaranteeModuleApi
import com.nimba.pv.CreatePvCommand
import com.nimba.pv.PvDebat
import com.nimba.pv.PvGuaranteeSnapshot
import com.nimba.pv.PvInfo
import com.nimba.pv.PvModuleApi
import com.nimba.pv.PvSnapshot
import com.nimba.pv.PvStatus
import com.nimba.pv.UpdatePvDraftCommand
import com.nimba.workflow.WorkflowModuleApi
import com.nimba.workflow.WorkflowStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class PvModuleApiService(
    private val pvs: PvRepository,
    private val debatRows: PvDebatRowRepository,
    private val guaranteeSnapshotRows: PvGuaranteeSnapshotRowRepository,
    private val creditCases: CreditCaseModuleApi,
    private val amortizationSchedules: AmortizationScheduleModuleApi,
    private val guarantees: GuaranteeModuleApi,
    private val workflow: WorkflowModuleApi,
    private val analysisSheets: AnalysisSheetModuleApi,
) : PvModuleApi {
    @Transactional(readOnly = true)
    override fun findByCase(creditCaseId: UUID): PvInfo? = pvs.findByCreditCaseId(creditCaseId)?.toInfo()

    @Transactional
    override fun create(command: CreatePvCommand): PvInfo {
        creditCases.getOrThrow(command.creditCaseId)
        if (workflow.statusOf(command.creditCaseId) != WorkflowStatus.APPROUVE) {
            throw conflict("Le dossier doit être approuvé par le comité avant de générer le PV")
        }
        if (pvs.existsByCreditCaseId(command.creditCaseId)) {
            throw conflict("Un PV existe déjà pour ce dossier")
        }
        val saved =
            pvs.save(
                Pv(
                    creditCaseId = command.creditCaseId,
                    createdBy = command.createdBy,
                    seanceDate = command.seanceDate,
                ),
            )
        return saved.toInfo()
    }

    @Transactional
    override fun updateDraft(
        creditCaseId: UUID,
        command: UpdatePvDraftCommand,
    ): PvInfo {
        val pv = requireDraft(creditCaseId)
        pv.seanceDate = command.seanceDate
        pv.rapporteur = command.rapporteur?.takeIf { it.isNotBlank() }
        pv.president = command.president?.takeIf { it.isNotBlank() }
        pv.updatedAt = Instant.now()

        val pvId = requireNotNull(pv.id)
        debatRows.deleteByPvId(pvId)
        command.debats.forEachIndexed { index, debat ->
            debatRows.save(PvDebatRow(pvId, debat.preoccupation, debat.reponse, debat.recommandation, index))
        }
        return pv.toInfo()
    }

    @Transactional
    override fun finalize(creditCaseId: UUID): PvInfo {
        val pv = requireDraft(creditCaseId)
        val case = creditCases.getOrThrow(creditCaseId)
        val articulation =
            amortizationSchedules.scheduleSummary(creditCaseId)
                ?: throw conflict("Aucun échéancier importé pour ce dossier")

        val faSections = analysisSheets.sections(creditCaseId).associateBy { it.key }

        pv.identitySnapshot = case.clientIdentity.toSnapshot()
        pv.articulationSnapshot = articulation.toSnapshot()
        pv.conditionsSnapshot = case.conditionsDeBanque.toSnapshot()
        pv.snapPointsForts = faSections[FaSectionKey.CONCLUSION_POINTS_FORTS]?.contentJson
        pv.snapPointsFaibles = faSections[FaSectionKey.CONCLUSION_POINTS_FAIBLES]?.contentJson
        pv.status = PvStatus.FINAL
        pv.finalizedAt = Instant.now()
        pv.updatedAt = pv.finalizedAt!!

        val pvId = requireNotNull(pv.id)
        guarantees.listByCase(creditCaseId).forEach { guarantee ->
            guaranteeSnapshotRows.save(PvGuaranteeSnapshotRow(pvId, guarantee.kind, guarantee.description))
        }
        return pv.toInfo()
    }

    private fun requireDraft(creditCaseId: UUID): Pv {
        val pv =
            pvs.findByCreditCaseId(creditCaseId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun PV pour ce dossier")
        if (pv.status != PvStatus.DRAFT) {
            throw conflict("Le PV est déjà finalisé")
        }
        return pv
    }

    private fun conflict(message: String) = ResponseStatusException(HttpStatus.CONFLICT, message)

    private fun Pv.toInfo(): PvInfo {
        val pvId = requireNotNull(id)
        return PvInfo(
            id = pvId,
            creditCaseId = creditCaseId,
            status = status,
            seanceDate = seanceDate,
            rapporteur = rapporteur,
            president = president,
            debats = debatRows.findByPvIdOrderByOrdreAsc(pvId).map { PvDebat(it.preoccupation, it.reponse, it.recommandation) },
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            finalizedAt = finalizedAt,
            snapshot = if (status == PvStatus.FINAL) toSnapshot(pvId) else null,
        )
    }

    private fun Pv.toSnapshot(pvId: UUID): PvSnapshot {
        // Hibernate reloads an `@Embedded` value object as null (not an empty
        // instance) once every one of its columns is null — true whenever the
        // dossier's identité/conditions de banque were never captured before
        // finalization (both are optional, incremental data). articulationSnapshot
        // is exempt: loanAmount/durationMonths always come from a real TA, so that
        // embeddable's columns are never ALL null.
        val identity = identitySnapshot ?: PvIdentitySnapshot()
        val articulation = requireNotNull(articulationSnapshot)
        val conditions = conditionsSnapshot ?: PvConditionsSnapshot()
        return PvSnapshot(
            identite =
                ClientIdentityInfo(
                    formeJuridique = identity.formeJuridique,
                    dateCreation = identity.dateCreation,
                    adressePhysique = identity.adressePhysique,
                    activiteDeBase = identity.activiteDeBase,
                    codeNif = identity.codeNif,
                    principalDirigeant = identity.principalDirigeant,
                    dateEntreeRelation = identity.dateEntreeRelation,
                    dateDerniereVisite = identity.dateDerniereVisite,
                    agence = identity.agence,
                    gestionnaire = identity.gestionnaire,
                    analyste = identity.analyste,
                    cotationPrecedente = identity.cotationPrecedente,
                    cotationActuelle = identity.cotationActuelle,
                ),
            articulation =
                ScheduleSummary(
                    loanAmount = requireNotNull(articulation.loanAmount),
                    durationMonths = requireNotNull(articulation.durationMonths),
                    startDate = null,
                    endDate = null,
                    totalEquipement = requireNotNull(articulation.totalEquipement),
                    totalAssurance = requireNotNull(articulation.totalAssurance),
                    totalTracking = requireNotNull(articulation.totalTracking),
                    totalImmatriculation = requireNotNull(articulation.totalImmatriculation),
                    totalInteret = requireNotNull(articulation.totalInteret),
                    premierLoyerTtc = articulation.premierLoyerTtc,
                    loyerMensuelHt = articulation.loyerMensuelHt,
                    valeurResiduelle = articulation.valeurResiduelle,
                ),
            garanties = guaranteeSnapshotRows.findByPvId(pvId).map { PvGuaranteeSnapshot(it.kind, it.description) },
            conditionsDeBanque =
                ConditionsDeBanqueInfo(
                    tauxInteretPct = conditions.tauxInteretPct,
                    fraisMiseEnPlacePct = conditions.fraisMiseEnPlacePct,
                    comEngagementPct = conditions.comEngagementPct,
                    fraisEtudesPct = conditions.fraisEtudesPct,
                    valeurResiduellePct = conditions.valeurResiduellePct,
                    fraisDivers = conditions.fraisDivers,
                ),
            pointsForts = snapPointsForts,
            pointsFaibles = snapPointsFaibles,
        )
    }
}

private fun ClientIdentityInfo.toSnapshot(): PvIdentitySnapshot =
    PvIdentitySnapshot(
        formeJuridique = formeJuridique,
        dateCreation = dateCreation,
        adressePhysique = adressePhysique,
        activiteDeBase = activiteDeBase,
        codeNif = codeNif,
        principalDirigeant = principalDirigeant,
        dateEntreeRelation = dateEntreeRelation,
        dateDerniereVisite = dateDerniereVisite,
        agence = agence,
        gestionnaire = gestionnaire,
        analyste = analyste,
        cotationPrecedente = cotationPrecedente,
        cotationActuelle = cotationActuelle,
    )

private fun ScheduleSummary.toSnapshot(): PvArticulationSnapshot =
    PvArticulationSnapshot(
        loanAmount = loanAmount,
        durationMonths = durationMonths,
        totalEquipement = totalEquipement,
        totalAssurance = totalAssurance,
        totalTracking = totalTracking,
        totalImmatriculation = totalImmatriculation,
        totalInteret = totalInteret,
        premierLoyerTtc = premierLoyerTtc,
        loyerMensuelHt = loyerMensuelHt,
        valeurResiduelle = valeurResiduelle,
    )

private fun ConditionsDeBanqueInfo.toSnapshot(): PvConditionsSnapshot =
    PvConditionsSnapshot(
        tauxInteretPct = tauxInteretPct,
        fraisMiseEnPlacePct = fraisMiseEnPlacePct,
        comEngagementPct = comEngagementPct,
        fraisEtudesPct = fraisEtudesPct,
        valeurResiduellePct = valeurResiduellePct,
        fraisDivers = fraisDivers,
    )
