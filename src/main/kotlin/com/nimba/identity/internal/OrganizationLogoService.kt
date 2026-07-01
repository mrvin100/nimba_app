package com.nimba.identity.internal

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant

/** The organisation logo bytes with the content type to serve them with. */
data class OrganizationLogoObject(
    val bytes: ByteArray,
    val contentType: String,
)

/**
 * Manages the organisation logo (upload/replace/remove/read) in object storage. There
 * is one logo for the whole (mono-tenant) organisation; its key and content type are
 * persisted on the settings row, the bytes in MinIO. This is object-storage I/O glue —
 * verified against a running MinIO like the avatar service, not unit tests.
 */
@Service
class OrganizationLogoService(
    private val settings: OrganizationSettingsService,
    private val storage: OrganizationLogoStorage,
    private val clock: Clock,
) {
    @Transactional
    fun upload(file: MultipartFile): OrganizationSettings {
        val contentType = file.contentType.orEmpty()
        if (file.isEmpty || !contentType.startsWith("image/")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le logo doit être une image")
        }
        val row = settings.get()
        row.logoKey = storage.upload(contentType, file.bytes)
        row.logoContentType = contentType
        row.updatedAt = Instant.now(clock)
        return row
    }

    @Transactional
    fun delete(): OrganizationSettings {
        val row = settings.get()
        row.logoKey?.let { storage.delete(it) }
        row.logoKey = null
        row.logoContentType = null
        row.updatedAt = Instant.now(clock)
        return row
    }

    @Transactional(readOnly = true)
    fun read(): OrganizationLogoObject = find() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun logo")

    /** The stored logo, or null when none is configured (no exception). */
    @Transactional(readOnly = true)
    fun find(): OrganizationLogoObject? {
        val row = settings.get()
        val key = row.logoKey ?: return null
        return OrganizationLogoObject(storage.load(key), row.logoContentType ?: "application/octet-stream")
    }
}
