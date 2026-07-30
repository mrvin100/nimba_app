package com.nimba.client.internal

import com.nimba.client.ClientInfo
import com.nimba.client.ClientMatriculeCorrected
import com.nimba.client.ClientModuleApi
import com.nimba.client.CreateClientCommand
import com.nimba.client.UpdateClientCommand
import com.nimba.client.UpdateClientMatriculeCommand
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
class ClientModuleApiService(
    private val clients: ClientRepository,
    private val events: ApplicationEventPublisher,
) : ClientModuleApi {
    @Transactional
    override fun create(command: CreateClientCommand): ClientInfo {
        if (command.matricule != null && clients.existsByMatricule(command.matricule)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un client existe déjà avec ce matricule")
        }
        if (command.codeNif != null && clients.existsByCodeNif(command.codeNif)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un client existe déjà avec ce numéro d'identification fiscale")
        }
        val client =
            Client(
                matricule = command.matricule,
                raisonSociale = command.raisonSociale,
                createdBy = command.createdBy,
            ).apply {
                type = command.type
                sigle = command.sigle
                formeJuridique = command.formeJuridique
                dateCreation = command.dateCreation
                adressePhysique = command.adressePhysique
                activiteDeBase = command.activiteDeBase
                codeNif = command.codeNif
                rccm = command.rccm
                accountNumber = command.accountNumber
                principalDirigeant = command.principalDirigeant
                dateEntreeRelation = command.dateEntreeRelation
                dateDerniereVisite = command.dateDerniereVisite
                agence = command.agence
                gestionnaire = command.gestionnaire
                analyste = command.analyste
                cotationPrecedente = command.cotationPrecedente
                cotationActuelle = command.cotationActuelle
            }
        return clients.save(client).toInfo()
    }

    @Transactional
    override fun update(
        id: UUID,
        command: UpdateClientCommand,
    ): ClientInfo {
        val client = clients.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable") }
        if (command.codeNif != null && clients.existsByCodeNifAndIdNot(command.codeNif, id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un client existe déjà avec ce numéro d'identification fiscale")
        }
        client.apply {
            raisonSociale = command.raisonSociale
            sigle = command.sigle
            formeJuridique = command.formeJuridique
            dateCreation = command.dateCreation
            adressePhysique = command.adressePhysique
            activiteDeBase = command.activiteDeBase
            codeNif = command.codeNif
            rccm = command.rccm
            accountNumber = command.accountNumber
            principalDirigeant = command.principalDirigeant
            dateEntreeRelation = command.dateEntreeRelation
            dateDerniereVisite = command.dateDerniereVisite
            agence = command.agence
            gestionnaire = command.gestionnaire
            analyste = command.analyste
            cotationPrecedente = command.cotationPrecedente
            cotationActuelle = command.cotationActuelle
            updatedAt = Instant.now()
        }
        return client.toInfo()
    }

    @Transactional
    override fun updateMatricule(
        id: UUID,
        command: UpdateClientMatriculeCommand,
    ): ClientInfo {
        val client = clients.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Client introuvable") }
        val matricule = command.matricule?.takeIf { it.isNotBlank() }
        if (matricule != null && clients.existsByMatriculeAndIdNot(matricule, id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Un client existe déjà avec ce matricule")
        }
        val previousMatricule = client.matricule
        client.matricule = matricule
        client.updatedAt = Instant.now()
        if (previousMatricule != null && matricule != null && previousMatricule != matricule) {
            events.publishEvent(ClientMatriculeCorrected(id, previousMatricule, matricule))
        }
        return client.toInfo()
    }

    @Transactional(readOnly = true)
    override fun findById(id: UUID): ClientInfo? = clients.findById(id).orElse(null)?.toInfo()

    @Transactional(readOnly = true)
    override fun findByIds(ids: Collection<UUID>): List<ClientInfo> = clients.findAllById(ids).map { it.toInfo() }

    @Transactional(readOnly = true)
    override fun findByMatricule(matricule: String): ClientInfo? = clients.findByMatricule(matricule)?.toInfo()

    @Transactional(readOnly = true)
    override fun list(pageable: Pageable): Page<ClientInfo> = clients.findAll(pageable).map { it.toInfo() }
}

private fun Client.toInfo(): ClientInfo =
    ClientInfo(
        id = requireNotNull(id),
        type = type,
        matricule = matricule,
        raisonSociale = raisonSociale,
        sigle = sigle,
        formeJuridique = formeJuridique,
        dateCreation = dateCreation,
        adressePhysique = adressePhysique,
        activiteDeBase = activiteDeBase,
        codeNif = codeNif,
        rccm = rccm,
        accountNumber = accountNumber,
        principalDirigeant = principalDirigeant,
        dateEntreeRelation = dateEntreeRelation,
        dateDerniereVisite = dateDerniereVisite,
        agence = agence,
        gestionnaire = gestionnaire,
        analyste = analyste,
        cotationPrecedente = cotationPrecedente,
        cotationActuelle = cotationActuelle,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
