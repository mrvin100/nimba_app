package com.nimba.fmp.internal

import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.getOrThrow
import com.nimba.fmp.CreateFmpCommand
import com.nimba.fmp.FmpInfo
import com.nimba.fmp.FmpModuleApi
import com.nimba.identity.IdentityModuleApi
import com.nimba.pv.PvInfo
import com.nimba.pv.PvModuleApi
import com.nimba.pv.PvStatus
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class FmpModuleApiService(
    private val fmps: FmpRepository,
    private val creditCases: CreditCaseModuleApi,
    private val pvs: PvModuleApi,
    private val identity: IdentityModuleApi,
) : FmpModuleApi {
    @Transactional(readOnly = true)
    override fun findByCase(creditCaseId: UUID): FmpInfo? {
        val fmp = fmps.findByCreditCaseId(creditCaseId) ?: return null
        val case = creditCases.getOrThrow(creditCaseId)
        val pv = requireNotNull(pvs.findByCase(creditCaseId)) { "A generated FMP always has a finalized PV" }
        return fmp.toInfo(case, pv)
    }

    @Transactional
    override fun create(command: CreateFmpCommand): FmpInfo {
        val case = creditCases.getOrThrow(command.creditCaseId)
        val pv = pvs.findByCase(command.creditCaseId)
        if (pv == null || pv.status != PvStatus.FINAL) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Le PV doit être finalisé avant de générer la fiche de mise en place",
            )
        }
        if (fmps.existsByCreditCaseId(command.creditCaseId)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Une fiche de mise en place existe déjà pour ce dossier")
        }
        val saved =
            fmps.save(
                Fmp(
                    creditCaseId = command.creditCaseId,
                    createdBy = command.createdBy,
                    numeroPret = command.numeroPret,
                    garantieRef = command.garantieRef?.takeIf { it.isNotBlank() },
                ),
            )
        return saved.toInfo(case, pv)
    }

    private fun Fmp.toInfo(
        case: CreditCaseInfo,
        pv: PvInfo,
    ): FmpInfo {
        val snapshot = requireNotNull(pv.snapshot) { "A FINAL PV always carries a snapshot" }
        val creator = identity.findUser(case.createdBy)
        return FmpInfo(
            id = requireNotNull(id),
            creditCaseId = creditCaseId,
            numeroPret = numeroPret,
            garantieRef = garantieRef,
            createdBy = createdBy,
            createdAt = createdAt,
            caseNumber = case.caseNumber,
            clientName = case.clientName,
            accountNumber = case.accountNumber,
            gfcEnCharge = "DRI/${initialsOf(creator?.fullName)}",
            identite = snapshot.identite,
            articulation = snapshot.articulation,
            garanties = snapshot.garanties,
            conditionsDeBanque = snapshot.conditionsDeBanque,
        )
    }
}

/** "Mamadou Diallo" → "MD"; falls back to a dash when the creator has no resolvable name. */
private fun initialsOf(fullName: String?): String =
    fullName
        ?.trim()
        ?.split(Regex("\\s+"))
        ?.mapNotNull { it.firstOrNull()?.uppercaseChar() }
        ?.joinToString("")
        ?.takeIf { it.isNotEmpty() }
        ?: "—"
