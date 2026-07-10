package com.nimba.creditcase.internal

import com.nimba.creditcase.CaseTypePolicies
import com.nimba.creditcase.ClientIdentityInfo
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseCreated
import com.nimba.creditcase.CreditCaseDeleted
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.CreditCaseStatus
import com.nimba.creditcase.ProductType
import com.nimba.creditcase.UpdateClientIdentityCommand
import com.nimba.creditcase.UpdateCreditCaseCommand
import com.nimba.identity.IdentityModuleApi
import com.nimba.shared.getOrThrow
import org.springframework.context.ApplicationEventPublisher
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
    private val events: ApplicationEventPublisher,
) : CreditCaseModuleApi {
    @Transactional
    override fun createCase(command: CreateCreditCaseCommand): CreditCaseInfo {
        // The case is owned by the analyst who created it; the creator must resolve
        // to a real user through the identity module's API (no direct entity access).
        identity.findUser(command.createdBy)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Analyste inconnu")
        val contractType = requireValidContractType(command.productType, command.contractType)

        val saved =
            creditCases.save(
                CreditCase(
                    caseNumber = numberGenerator.nextCaseNumber(),
                    clientName = command.clientName,
                    productType = command.productType,
                    contractType = contractType,
                    currency = command.currency,
                    createdBy = command.createdBy,
                    accountNumber = command.accountNumber?.takeIf { it.isNotBlank() },
                ),
            )
        // Let the workflow module initialise the dossier's lifecycle in the same
        // transaction; the creditcase module stays unaware of who consumes this.
        events.publishEvent(CreditCaseCreated(requireNotNull(saved.id)))
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
        case.contractType = requireValidContractType(command.productType, command.contractType)
        case.currency = command.currency
        case.accountNumber = command.accountNumber?.takeIf { it.isNotBlank() }
        case.updatedAt = Instant.now()
        return case.toCreditCaseInfo()
    }

    @Transactional
    override fun updateIdentity(
        id: UUID,
        command: UpdateClientIdentityCommand,
    ): CreditCaseInfo {
        val case = creditCases.getOrThrow(id, "Dossier introuvable")
        case.clientIdentity = command.toClientIdentity()
        case.updatedAt = Instant.now()
        return case.toCreditCaseInfo()
    }

    @Transactional(readOnly = true)
    override fun list(
        pageable: Pageable,
        archived: Boolean?,
    ): Page<CreditCaseInfo> =
        when (archived) {
            null -> creditCases.findAll(pageable)
            true -> creditCases.findByArchivedAtIsNotNull(pageable)
            false -> creditCases.findByArchivedAtIsNull(pageable)
        }.map { it.toCreditCaseInfo() }

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

    @Transactional
    override fun archive(id: UUID): CreditCaseInfo {
        val case = creditCases.getOrThrow(id, "Dossier introuvable")
        // Idempotent: re-archiving keeps the original timestamp.
        if (case.archivedAt == null) {
            case.archivedAt = Instant.now()
            case.updatedAt = Instant.now()
        }
        return case.toCreditCaseInfo()
    }

    @Transactional
    override fun unarchive(id: UUID): CreditCaseInfo {
        val case = creditCases.getOrThrow(id, "Dossier introuvable")
        if (case.archivedAt != null) {
            case.archivedAt = null
            case.updatedAt = Instant.now()
        }
        return case.toCreditCaseInfo()
    }

    @Transactional
    override fun delete(id: UUID) {
        val case = creditCases.getOrThrow(id, "Dossier introuvable")
        // Synchronous listeners purge dependent data (schedules, trades, retained
        // files) inside this same transaction — all gone or nothing is.
        events.publishEvent(CreditCaseDeleted(requireNotNull(case.id)))
        creditCases.delete(case)
    }
}

/**
 * Validates the (productType, contractType) pair against [CaseTypePolicies] — the
 * single source of truth for which combinations exist — and returns the contract
 * type to persist. A contract type is required exactly when the product is LEASING
 * (each of its two sub-flavors has its own FA) and must be absent for every other
 * product; MC2/MUFFA carries no contract distinction.
 */
private fun requireValidContractType(
    productType: ProductType,
    contractType: ContractType?,
): ContractType? {
    if (CaseTypePolicies.find(productType, contractType) != null) return contractType
    val message =
        if (productType == ProductType.LEASING) {
            "Le type de contrat est requis pour un dossier LEASING"
        } else {
            "Le type de contrat ne s'applique pas à un dossier ${productType.name.replace('_', '/')}"
        }
    throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}

internal fun CreditCase.toCreditCaseInfo(): CreditCaseInfo =
    CreditCaseInfo(
        id = requireNotNull(id),
        caseNumber = caseNumber,
        clientName = clientName,
        productType = productType,
        contractType = contractType,
        currency = currency,
        status = status,
        createdBy = createdBy,
        createdAt = createdAt,
        accountNumber = accountNumber,
        archivedAt = archivedAt,
        clientIdentity = identityOrEmpty().toInfo(),
    )

private fun ClientIdentity.toInfo(): ClientIdentityInfo =
    ClientIdentityInfo(
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

private fun UpdateClientIdentityCommand.toClientIdentity(): ClientIdentity =
    ClientIdentity(
        formeJuridique = formeJuridique?.takeIf { it.isNotBlank() },
        dateCreation = dateCreation,
        adressePhysique = adressePhysique?.takeIf { it.isNotBlank() },
        activiteDeBase = activiteDeBase?.takeIf { it.isNotBlank() },
        codeNif = codeNif?.takeIf { it.isNotBlank() },
        principalDirigeant = principalDirigeant?.takeIf { it.isNotBlank() },
        dateEntreeRelation = dateEntreeRelation,
        dateDerniereVisite = dateDerniereVisite,
        agence = agence?.takeIf { it.isNotBlank() },
        gestionnaire = gestionnaire?.takeIf { it.isNotBlank() },
        analyste = analyste?.takeIf { it.isNotBlank() },
        cotationPrecedente = cotationPrecedente?.takeIf { it.isNotBlank() },
        cotationActuelle = cotationActuelle?.takeIf { it.isNotBlank() },
    )
