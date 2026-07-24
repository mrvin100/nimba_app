package com.nimba

import com.nimba.client.ClientModuleApi
import com.nimba.client.CreateClientCommand
import java.util.UUID

/**
 * Seeds a client and returns its id — the single source of client identity a credit
 * dossier now links to. [raisonSociale] doubles as the dossier's displayed client
 * name, so a test that asserts on the name passes it here. [createdBy] defaults to a
 * throwaway id (the client record's audit author is irrelevant to most tests).
 */
fun seedClient(
    clients: ClientModuleApi,
    raisonSociale: String = "Client Test",
    createdBy: UUID = UUID.randomUUID(),
    matricule: String? = null,
): UUID = clients.create(CreateClientCommand(matricule = matricule, raisonSociale = raisonSociale, createdBy = createdBy)).id
