package com.nimba.creditcase.internal

import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.identity.IdentityModuleApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
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
                ),
            )
        return saved.toCreditCaseInfo()
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): CreditCaseInfo? = creditCases.findById(id).map { it.toCreditCaseInfo() }.orElse(null)

    @Transactional(readOnly = true)
    override fun findByCaseNumber(caseNumber: String): CreditCaseInfo? = creditCases.findByCaseNumber(caseNumber)?.toCreditCaseInfo()
}

internal fun CreditCase.toCreditCaseInfo(): CreditCaseInfo =
    CreditCaseInfo(
        id = requireNotNull(id),
        caseNumber = caseNumber,
        clientName = clientName,
        productType = productType,
        currency = currency,
        createdBy = createdBy,
        createdAt = createdAt,
    )
