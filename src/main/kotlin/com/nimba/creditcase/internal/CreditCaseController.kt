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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
                ),
            ).toResponse()

    @GetMapping("/{id}")
    fun getById(
        @PathVariable id: UUID,
    ): CreditCaseResponse = creditCases.getOrThrow(id).toResponse()

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): PageResponse<CreditCaseSummaryResponse> = creditCases.list(pageable).toPageResponse(CreditCaseInfo::toSummaryResponse)
}
