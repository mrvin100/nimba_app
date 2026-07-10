package com.nimba.creditcase.internal

import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseInfo
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.UpdateCreditCaseCommand
import com.nimba.creditcase.getOrThrow
import com.nimba.shared.CurrentUser
import com.nimba.shared.PageResponse
import com.nimba.shared.toPageResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/credit-cases")
class CreditCaseController(
    private val creditCases: CreditCaseModuleApi,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreditCaseWriteRequest,
    ): CreditCaseResponse =
        creditCases
            .createCase(
                CreateCreditCaseCommand(
                    clientName = request.clientName,
                    productType = request.productType,
                    currency = request.currency,
                    createdBy = currentUser.id(),
                    accountNumber = request.accountNumber,
                    contractType = request.contractType,
                ),
            ).toResponse()

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreditCaseWriteRequest,
    ): CreditCaseResponse =
        creditCases
            .updateCase(
                id,
                UpdateCreditCaseCommand(
                    clientName = request.clientName,
                    productType = request.productType,
                    currency = request.currency,
                    accountNumber = request.accountNumber,
                    contractType = request.contractType,
                ),
            ).toResponse()

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): CreditCaseResponse = creditCases.getOrThrow(id).toResponse()

    @PutMapping("/{id}/identity")
    fun updateIdentity(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ClientIdentityRequest,
    ): CreditCaseResponse = creditCases.updateIdentity(id, request.toCommand()).toResponse()

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
        @RequestParam(required = false) archived: Boolean?,
    ): PageResponse<CreditCaseSummaryResponse> = creditCases.list(pageable, archived).toPageResponse(CreditCaseInfo::toSummaryResponse)

    // The three actions below are administrative acts on a dossier (NIMBA-45),
    // deliberately outside the DRI business flow — hence ROLE_ADMIN on top of the
    // URL matcher that opens exactly these paths to administrators.

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    fun archive(
        @PathVariable id: UUID,
    ): CreditCaseResponse = creditCases.archive(id).toResponse()

    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('ADMIN')")
    fun unarchive(
        @PathVariable id: UUID,
    ): CreditCaseResponse = creditCases.unarchive(id).toResponse()

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = creditCases.delete(id)
}
