package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionFieldRegistry
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionModuleApi
import com.nimba.caution.CautionStatus
import com.nimba.caution.CreateCautionCommand
import com.nimba.caution.UpdateCautionCommand
import com.nimba.client.ClientModuleApi
import com.nimba.client.getOrThrow
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.util.UUID

@Service
class CautionModuleApiService(
    private val cautions: CautionRepository,
    private val clients: ClientModuleApi,
    private val numberGenerator: CautionNumberGenerator,
    private val objectMapper: ObjectMapper,
) : CautionModuleApi {
    @Transactional
    override fun create(command: CreateCautionCommand): CautionInfo {
        val client = clients.getOrThrow(command.clientId)
        requireRequiredFields(command.documentType, command.content)

        val caution =
            cautions.save(
                Caution(
                    clientId = client.id,
                    documentType = command.documentType,
                    referenceNumber = numberGenerator.nextReferenceNumber(client.matricule, command.documentType),
                    createdBy = command.createdBy,
                ).apply {
                    contentJson = objectMapper.writeValueAsString(command.content)
                },
            )
        return caution.toInfo(objectMapper)
    }

    @Transactional
    override fun update(
        id: UUID,
        command: UpdateCautionCommand,
    ): CautionInfo {
        val caution = requireDraft(id)
        requireRequiredFields(caution.documentType, command.content)
        caution.contentJson = objectMapper.writeValueAsString(command.content)
        caution.updatedAt = Instant.now()
        return caution.toInfo(objectMapper)
    }

    @Transactional
    override fun finalize(id: UUID): CautionInfo {
        val caution = requireDraft(id)
        val client = clients.getOrThrow(caution.clientId)
        caution.clientSnapshot =
            CautionClientSnapshot(
                matricule = client.matricule,
                raisonSociale = client.raisonSociale,
                sigle = client.sigle,
                adressePhysique = client.adressePhysique,
                rccm = client.rccm,
                accountNumber = client.accountNumber,
                agence = client.agence,
            )
        caution.status = CautionStatus.FINAL
        caution.finalizedAt = Instant.now()
        caution.updatedAt = caution.finalizedAt!!
        return caution.toInfo(objectMapper)
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): CautionInfo? = cautions.findById(id).orElse(null)?.toInfo(objectMapper)

    @Transactional(readOnly = true)
    override fun list(
        pageable: Pageable,
        clientId: UUID?,
        documentType: CautionDocumentType?,
        status: CautionStatus?,
    ): Page<CautionInfo> = cautions.search(clientId, documentType, status, pageable).map { it.toInfo(objectMapper) }

    @Transactional
    override fun delete(id: UUID) {
        val caution = cautions.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable") }
        if (caution.status != CautionStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Seul un brouillon peut être supprimé — un document finalisé est une pièce officielle",
            )
        }
        cautions.delete(caution)
    }

    private fun requireDraft(id: UUID): Caution {
        val caution = cautions.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable") }
        if (caution.status != CautionStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La caution est déjà finalisée")
        }
        return caution
    }

    private fun requireRequiredFields(
        documentType: CautionDocumentType,
        content: Map<String, String>,
    ) {
        val missing =
            CautionFieldRegistry
                .allFieldsFor(documentType)
                .filter { content[it.key].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Champs requis manquants : ${missing.joinToString(", ") { it.label }}",
            )
        }
    }

    private fun Caution.toInfo(objectMapper: ObjectMapper): CautionInfo =
        CautionInfo(
            id = requireNotNull(id),
            clientId = clientId,
            documentType = documentType,
            referenceNumber = referenceNumber,
            status = status,
            content = objectMapper.readValue<Map<String, String>>(contentJson),
            clientSnapshot = clientSnapshot?.toInfo(),
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
            finalizedAt = finalizedAt,
        )

    private fun CautionClientSnapshot.toInfo(): CautionClientSnapshotInfo =
        CautionClientSnapshotInfo(
            matricule = requireNotNull(matricule),
            raisonSociale = requireNotNull(raisonSociale),
            sigle = sigle,
            adressePhysique = adressePhysique,
            rccm = rccm,
            accountNumber = accountNumber,
            agence = agence,
        )
}
