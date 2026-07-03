package com.nimba.creditcase.internal

import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.UpdateCreditCaseCommand
import com.nimba.identity.IdentityModuleApi
import com.nimba.shared.getOrThrow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class CreditCaseModuleApiService(
    private val creditCases: CreditCaseRepository,
    private val numberGenerator: CreditCaseNumberGenerator,
    private val identity: IdentityModuleApi,
) : CreditCaseModuleApi {
    @Transactional
    override fun createCase(command: CreateCreditCaseCommand): CreditCaseInfo {
        // The case is owned by the analyst who created it; the creator must resolve
        // to a real user through the identity module's API (no direct entity access).
        identity.findUser(command.createdBy)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Analyste inconnu")

        val saved =
            creditCases.save(
                CreditCase(
                    caseNumber = numberGenerator.nextCaseNumber(),
                    clientName = command.clientName,
                    productType = command.productType,
                    currency = command.currency,
                    createdBy = command.createdBy,
                    accountNumber = command.accountNumber?.takeIf { it.isNotBlank() },
                ),
            )
        return saved.toCreditCaseInfo()
    }

    @Transactional
    override fun updateCase(
        id: UUID,
        command: UpdateCreditCaseCommand,
    ): CreditCaseInfo {
        val case = creditCases.getOrThrow(id, "Dossier introuvable")
        case.clientName = command.clientName
        case.productType = command.productType
        case.currency = command.currency
        case.accountNumber = command.accountNumber?.takeIf { it.isNotBlank() }
        case.updatedAt = Instant.now()
        return case.toCreditCaseInfo()
    }

    @Transactional(readOnly = true)
    override fun list(pageable: Pageable): Page<CreditCaseInfo> = creditCases.findAll(pageable).map { it.toCreditCaseInfo() }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): CreditCaseInfo? = creditCases.findById(id).map { it.toCreditCaseInfo() }.orElse(null)

    @Transactional(readOnly = true)
    override fun findByCaseNumber(caseNumber: String): CreditCaseInfo? = creditCases.findByCaseNumber(caseNumber)?.toCreditCaseInfo()

    @Transactional
    override fun markTradesGenerated(creditCaseId: UUID) {
        val case = creditCases.getOrThrow(creditCaseId, "Dossier introuvable")
        case.status = CreditCaseStatus.TRADES_GENERES
        case.updatedAt = Instant.now()
    }
}

internal fun CreditCase.toCreditCaseInfo(): CreditCaseInfo =
    CreditCaseInfo(
        id = requireNotNull(id),
        caseNumber = caseNumber,
        clientName = clientName,
        productType = productType,
        currency = currency,
        status = status,
        createdBy = createdBy,
        createdAt = createdAt,
        accountNumber = accountNumber,
    )
