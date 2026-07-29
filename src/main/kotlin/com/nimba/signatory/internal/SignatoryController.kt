package com.nimba.signatory.internal

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The signatory picker's candidate list — open to any authenticated user (any
 * direction may need to designate a signatory on a document it produces), already
 * filtered to what the caller is authorized to pick. Management of standalone
 * signatories is a separate admin-only surface, see [AdminSignatoryController].
 */
@RestController
@RequestMapping("/signatories")
class SignatoryController(
    private val service: SignatoryService,
) {
    @GetMapping
    fun options(): List<SignatoryOptionResponse> = service.options()
}
