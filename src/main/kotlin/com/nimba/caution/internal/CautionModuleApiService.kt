package com.nimba.caution.internal

import com.nimba.caution.CautionClientSnapshotInfo
import com.nimba.caution.CautionDocumentType
import com.nimba.caution.CautionDossierDeleted
import com.nimba.caution.CautionDossierEventInfo
import com.nimba.caution.CautionDossierInfo
import com.nimba.caution.CautionFieldRegistry
import com.nimba.caution.CautionInfo
import com.nimba.caution.CautionModuleApi
import com.nimba.caution.CautionStatus
import com.nimba.caution.CreateCautionCommand
import com.nimba.caution.CreateDossierCommand
import com.nimba.caution.DossierAction
import com.nimba.caution.DossierStatus
import com.nimba.caution.UpdateCautionCommand
import com.nimba.client.ClientModuleApi
import com.nimba.client.getOrThrow
import org.springframework.context.ApplicationEventPublisher
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
    private val dossiers: CautionDossierRepository,
    private val dossierEvents: CautionDossierEventRepository,
    private val clients: ClientModuleApi,
    private val numberGenerator: CautionNumberGenerator,
    private val objectMapper: ObjectMapper,
    private val events: ApplicationEventPublisher,
) : CautionModuleApi {
    @Transactional
    override fun create(command: CreateCautionCommand): CautionInfo {
        val client = clients.getOrThrow(command.clientId)
        requireRequiredFields(command.documentType, command.content)
        command.dossierId?.let {
            requireDossierForClient(it, client.id)
            assertWritable(requireDossier(it))
        }

        val caution =
            cautions.save(
                Caution(
                    clientId = client.id,
                    documentType = command.documentType,
                    referenceNumber =
                        numberGenerator.nextReferenceNumber(
                            client.matricule,
                            command.documentType,
                            command.startingReferenceSequence,
                        ),
                    createdBy = command.createdBy,
                ).apply {
                    dossierId = command.dossierId
                    contentJson = objectMapper.writeValueAsString(command.content)
                },
            )
        return caution.toInfo(objectMapper)
    }

    @Transactional
    override fun createDossier(command: CreateDossierCommand): CautionDossierInfo {
        val client = clients.getOrThrow(command.clientId)
        val dossier =
            dossiers.save(
                CautionDossier(
                    clientId = client.id,
                    referenceNumber = numberGenerator.nextDossierReferenceNumber(client.matricule, command.startingReferenceSequence),
                    createdBy = command.createdBy,
                ).apply {
                    contentJson = objectMapper.writeValueAsString(command.content)
                },
            )
        return dossier.toInfo(objectMapper)
    }

    @Transactional
    override fun updateDossier(
        id: UUID,
        content: Map<String, String>,
    ): CautionDossierInfo {
        val dossier = requireDossier(id)
        assertWritable(dossier)
        dossier.contentJson = objectMapper.writeValueAsString(content)
        // An amendment re-issues the companions: bump the version they carry.
        dossier.version += 1
        dossier.updatedAt = Instant.now()
        return dossier.toInfo(objectMapper)
    }

    @Transactional
    override fun finalizeDossier(
        id: UUID,
        actor: UUID,
    ): CautionDossierInfo {
        val dossier = requireDossier(id)
        if (dossier.status != DossierStatus.BROUILLON) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Seul un dossier en brouillon peut être finalisé")
        }
        cautions
            .findByDossierIdOrderByCreatedAtDesc(id)
            .filter { it.status == CautionStatus.DRAFT }
            .forEach { freeze(it) }
        return transition(dossier, DossierAction.FINALIZE, DossierStatus.FINALISE, actor, null)
    }

    @Transactional
    override fun prorogeDossier(
        id: UUID,
        actor: UUID,
        reason: String,
    ): CautionDossierInfo {
        require(reason.isNotBlank()) { "reason must not be blank" }
        val dossier = requireDossier(id)
        if (dossier.status != DossierStatus.FINALISE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Seul un dossier finalisé peut être prorogé")
        }
        return transition(dossier, DossierAction.PROROGE, DossierStatus.EN_PROROGATION, actor, reason)
    }

    @Transactional
    override fun refinalizeDossier(
        id: UUID,
        actor: UUID,
    ): CautionDossierInfo {
        val dossier = requireDossier(id)
        if (dossier.status != DossierStatus.EN_PROROGATION) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Seul un dossier en prorogation peut être re-finalisé")
        }
        dossier.version += 1
        return transition(dossier, DossierAction.REFINALIZE, DossierStatus.FINALISE, actor, null)
    }

    @Transactional(readOnly = true)
    override fun dossierEvents(id: UUID): List<CautionDossierEventInfo> =
        dossierEvents.findByDossierIdOrderByCreatedAtDesc(id).map { it.toInfo() }

    @Transactional
    override fun deleteDossier(id: UUID) {
        val dossier = requireDossier(id)
        cautions.deleteByDossierId(requireNotNull(dossier.id))
        dossierEvents.deleteByDossierId(requireNotNull(dossier.id))
        dossiers.delete(dossier)
        events.publishEvent(CautionDossierDeleted(requireNotNull(dossier.id)))
    }

    /** Applies a lifecycle transition and appends it to the dossier's journal. */
    private fun transition(
        dossier: CautionDossier,
        action: DossierAction,
        to: DossierStatus,
        actor: UUID,
        reason: String?,
    ): CautionDossierInfo {
        val from = dossier.status
        dossier.status = to
        dossier.updatedAt = Instant.now()
        dossierEvents.save(
            CautionDossierEvent(
                dossierId = requireNotNull(dossier.id),
                action = action,
                fromStatus = from,
                toStatus = to,
                reason = reason,
                actor = actor,
            ),
        )
        return dossier.toInfo(objectMapper)
    }

    /** A dossier accepts writes (add/edit/delete of documents and common info) only while BROUILLON or EN_PROROGATION. */
    private fun assertWritable(dossier: CautionDossier) {
        if (dossier.status == DossierStatus.FINALISE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Le dossier est finalisé : déverrouillez-le (prorogation) pour le modifier")
        }
    }

    /** Freezes a document: captures the issuing client's identity and marks it FINAL. Shared by document- and dossier-level finalization. */
    private fun freeze(caution: Caution) {
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
    }

    private fun CautionDossierEvent.toInfo(): CautionDossierEventInfo =
        CautionDossierEventInfo(
            id = requireNotNull(id),
            action = action,
            fromStatus = fromStatus,
            toStatus = toStatus,
            reason = reason,
            actor = actor,
            createdAt = createdAt,
        )

    private fun requireDossier(id: UUID): CautionDossier =
        dossiers.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable") }

    @Transactional(readOnly = true)
    override fun findDossier(id: UUID): CautionDossierInfo? = dossiers.findById(id).orElse(null)?.toInfo(objectMapper)

    @Transactional(readOnly = true)
    override fun listDossiers(
        pageable: Pageable,
        clientId: UUID?,
    ): Page<CautionDossierInfo> = dossiers.search(clientId, pageable).map { it.toInfo(objectMapper) }

    @Transactional(readOnly = true)
    override fun dossierDocuments(dossierId: UUID): List<CautionInfo> =
        cautions.findByDossierIdOrderByCreatedAtDesc(dossierId).map { it.toInfo(objectMapper) }

    private fun requireDossierForClient(
        dossierId: UUID,
        clientId: UUID,
    ) {
        val dossier =
            dossiers.findById(dossierId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable")
            }
        if (dossier.clientId != clientId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le dossier appartient à un autre client")
        }
    }

    @Transactional
    override fun update(
        id: UUID,
        command: UpdateCautionCommand,
    ): CautionInfo {
        val caution = requireEditable(id)
        requireRequiredFields(caution.documentType, command.content)
        caution.contentJson = objectMapper.writeValueAsString(command.content)
        caution.updatedAt = Instant.now()
        return caution.toInfo(objectMapper)
    }

    @Transactional
    override fun finalize(id: UUID): CautionInfo {
        val caution = requireDraft(id)
        freeze(caution)
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

    @Transactional(readOnly = true)
    override fun referenceSequenceInitialized(): Boolean = numberGenerator.isInitialized()

    @Transactional
    override fun delete(id: UUID) {
        cautions.delete(requireEditable(id))
    }

    /**
     * Resolves an editable document. Within a dossier, editability follows the
     * dossier's lock (writable while BROUILLON or EN_PROROGATION); a legacy
     * standalone document falls back to its own DRAFT status.
     */
    private fun requireEditable(id: UUID): Caution {
        val caution = cautions.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Caution introuvable") }
        val dossierId = caution.dossierId
        if (dossierId != null) {
            assertWritable(requireDossier(dossierId))
        } else if (caution.status != CautionStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Seul un brouillon peut être modifié — un document finalisé est une pièce officielle",
            )
        }
        return caution
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
                .filter { !it.optional && content[it.key].isNullOrBlank() }
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

    private fun CautionDossier.toInfo(objectMapper: ObjectMapper): CautionDossierInfo =
        CautionDossierInfo(
            id = requireNotNull(id),
            clientId = clientId,
            referenceNumber = referenceNumber,
            status = status,
            version = version,
            content = objectMapper.readValue<Map<String, String>>(contentJson),
            createdBy = createdBy,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}
