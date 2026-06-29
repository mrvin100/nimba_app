package com.nimba.creditcase.internal

import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/credit-cases")
class CreditCaseController(
    private val creditCases: CreditCaseModuleApi,
    private val creditCaseRepository: CreditCaseRepository,
    private val currentUser: CurrentUser,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: CreateCreditCaseRequest,
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

    @GetMapping
    fun list(
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): PagedResponse<CreditCaseSummaryResponse> {
        val page = creditCaseRepository.findAll(pageable).map { it.toSummaryResponse() }
        return PagedResponse(
            content = page.content,
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            hasNext = page.hasNext(),
            hasPrevious = page.hasPrevious(),
        )
    }
}
