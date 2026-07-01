package com.nimba.identity.internal

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * One-time first-admin bootstrap (NIMBA-35). Public and permitted in the security
 * config; the service refuses once any admin exists. GET reports availability so
 * the frontend can show or hide the bootstrap screen.
 */
@RestController
@RequestMapping("/auth/bootstrap")
class BootstrapController(
    private val bootstrap: BootstrapService,
) {
    @GetMapping
    fun status(): BootstrapStatusResponse = BootstrapStatusResponse(bootstrap.available())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: BootstrapRequest,
    ): AdminUserResponse = bootstrap.bootstrap(request)
}
