package com.nimba.pv.internal

import com.nimba.pv.PvModuleApi
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@RestController
@RequestMapping("/credit-cases/{caseId}/pv")
class PvController(
    private val pvs: PvModuleApi,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: CreatePvRequest,
    ): PvResponse = pvs.create(request.toCommand(caseId, currentUser.id())).toResponse()

    @GetMapping
    fun get(
        @PathVariable caseId: UUID,
    ): PvResponse = pvs.findByCase(caseId)?.toResponse() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun PV pour ce dossier")

    @PutMapping
    fun updateDraft(
        @PathVariable caseId: UUID,
        @Valid @RequestBody request: UpdatePvDraftRequest,
    ): PvResponse = pvs.updateDraft(caseId, request.toCommand()).toResponse()

    @PostMapping("/finalize")
    fun finalize(
        @PathVariable caseId: UUID,
    ): PvResponse = pvs.finalize(caseId).toResponse()
}
