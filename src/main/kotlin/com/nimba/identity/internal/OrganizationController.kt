package com.nimba.identity.internal

import jakarta.validation.Valid
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Organisation settings API (NIMBA-35). Under the admin path tree, so it requires
 * ROLE_ADMIN (security config). Governs the invitation e-mail sender identity, the
 * organisation name, and the logo printed on generated documents.
 */
@RestController
@RequestMapping("/admin/organization")
class OrganizationController(
    private val organization: OrganizationSettingsService,
    private val logos: OrganizationLogoService,
) {
    @GetMapping
    fun get(): OrganizationResponse = organization.get().toResponse()

    @PutMapping
    fun update(
        @Valid @RequestBody request: UpdateOrganizationRequest,
    ): OrganizationResponse = organization.update(request).toResponse()

    @PostMapping("/logo", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadLogo(
        @RequestParam("file") file: MultipartFile,
    ): OrganizationResponse = logos.upload(file).toResponse()

    @DeleteMapping("/logo")
    fun deleteLogo(): OrganizationResponse = logos.delete().toResponse()

    @GetMapping("/logo")
    fun logo(): ResponseEntity<ByteArray> {
        val logo = logos.read()
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(logo.contentType))
            .cacheControl(CacheControl.noCache())
            .body(logo.bytes)
    }
}
