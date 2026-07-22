package com.nimba.client.internal

import com.nimba.client.ClientInfo
import com.nimba.client.ClientModuleApi
import com.nimba.client.getOrThrow
import com.nimba.shared.CurrentUser
import com.nimba.shared.PageResponse
import com.nimba.shared.toPageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The client registry's surface — DCM-only for now (it exists to back the
 * Caution module). See [ClientModuleApi]'s KDoc for why a client is not tied
 * to a credit case.
 */
@RestController
@RequestMapping("/clients")
class ClientController(
    private val clients: ClientModuleApi,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateClientRequest,
    ): ClientResponse = clients.create(request.toCommand(currentUser.id())).toResponse()

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateClientRequest,
    ): ClientResponse = clients.update(id, request.toCommand()).toResponse()

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): ClientResponse = clients.getOrThrow(id).toResponse()

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["raisonSociale"], direction = Sort.Direction.ASC) pageable: Pageable,
    ): PageResponse<ClientSummaryResponse> = clients.list(pageable).toPageResponse(ClientInfo::toSummaryResponse)
}
