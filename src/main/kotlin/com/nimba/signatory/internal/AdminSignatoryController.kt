package com.nimba.signatory.internal

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
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
 * Management of standalone signatories (someone without a user account). Under the
 * admin path tree, so it requires ROLE_ADMIN (security config) — creating a signatory
 * record for a person outside the system, and deciding who may pick them, is an
 * administrative act, unlike opting one's own profile in as a signatory.
 */
@RestController
@RequestMapping("/admin/signatories")
class AdminSignatoryController(
    private val service: SignatoryService,
) {
    @GetMapping
    fun list(): List<SignatoryResponse> = service.list()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: SignatoryWriteRequest,
    ): SignatoryResponse = service.create(request)

    @PutMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SignatoryWriteRequest,
    ): SignatoryResponse = service.update(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: UUID,
    ) = service.delete(id)
}
