package com.nimba.creditcase.internal

import com.nimba.creditcase.CreateCreditCaseCommand
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.shared.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/credit-cases")
class CreditCaseController(
    private val creditCases: CreditCaseModuleApi,
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
}
