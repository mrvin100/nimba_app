package com.nimba.analysissheet.internal

import com.nimba.analysissheet.AnalysisSheetStatus
import com.nimba.analysissheet.FaSectionImageInfo
import com.nimba.analysissheet.FaSectionKey
import com.nimba.analysissheet.FaSectionRegistry
import com.nimba.analysissheet.FaSectionType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** One image's binary, ready to stream back. */
data class AnalysisSheetImageObject(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
)

/**
 * The analysis-sheet module's only MinIO-touching service, isolated from
 * [AnalysisSheetModuleApiService] so that class's logic stays meaningfully
 * covered by tests — object-storage I/O is verified against a running MinIO,
 * not unit tests, matching the guarantee module's attachment service (see the
 * Kover exclude list). Writes obey the same gates as section content: the
 * sheet must be a draft and the key must be an IMAGE section of the sheet's
 * variant.
 */
@Service
class AnalysisSheetImageService(
    private val sheets: AnalysisSheetRepository,
    private val images: AnalysisSheetImageRepository,
    private val storage: AnalysisSheetImageStorage,
) {
    @Transactional
    fun add(
        creditCaseId: UUID,
        key: FaSectionKey,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
        caption: String?,
        uploadedBy: UUID,
    ): List<FaSectionImageInfo> {
        val sheet = requireDraft(creditCaseId)
        requireImageSection(sheet, key)
        if (!contentType.startsWith("image/") || bytes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier doit être une image")
        }
        val sheetId = requireNotNull(sheet.id)
        val imageId = UUID.randomUUID()
        val storageKey = storage.upload(sheetId, imageId, fileName, contentType, bytes)
        images.save(
            AnalysisSheetImage(
                analysisSheetId = sheetId,
                sectionKey = key,
                fileName = fileName,
                contentType = contentType,
                sizeBytes = bytes.size.toLong(),
                storageKey = storageKey,
                uploadedBy = uploadedBy,
                caption = caption?.takeIf { it.isNotBlank() },
            ).apply { id = imageId },
        )
        return list(sheetId, key)
    }

    @Transactional
    fun updateCaption(
        creditCaseId: UUID,
        key: FaSectionKey,
        imageId: UUID,
        caption: String?,
    ): List<FaSectionImageInfo> {
        val sheet = requireDraft(creditCaseId)
        val image = requireImage(sheet, key, imageId)
        image.caption = caption?.takeIf { it.isNotBlank() }
        return list(requireNotNull(sheet.id), key)
    }

    @Transactional
    fun remove(
        creditCaseId: UUID,
        key: FaSectionKey,
        imageId: UUID,
    ): List<FaSectionImageInfo> {
        val sheet = requireDraft(creditCaseId)
        val image = requireImage(sheet, key, imageId)
        images.delete(image)
        // Best-effort, after the row is gone in this transaction.
        runCatching { storage.delete(image.storageKey) }
        return list(requireNotNull(sheet.id), key)
    }

    @Transactional(readOnly = true)
    fun read(
        creditCaseId: UUID,
        key: FaSectionKey,
        imageId: UUID,
    ): AnalysisSheetImageObject {
        val sheet = requireSheet(creditCaseId)
        val image = requireImage(sheet, key, imageId)
        return AnalysisSheetImageObject(image.fileName, image.contentType, storage.load(image.storageKey))
    }

    /** All of a sheet's images grouped by section, for the section list and the export. */
    @Transactional(readOnly = true)
    fun imagesBySection(sheetId: UUID): Map<FaSectionKey, List<FaSectionImageInfo>> =
        images
            .findByAnalysisSheetIdOrderByUploadedAt(sheetId)
            .groupBy({ it.sectionKey }, { it.toInfo() })

    /** The stored binaries of one section, in embedding order — the export's reader. */
    @Transactional(readOnly = true)
    fun sectionImageObjects(
        sheetId: UUID,
        key: FaSectionKey,
    ): List<Pair<FaSectionImageInfo, ByteArray>> =
        images
            .findByAnalysisSheetIdAndSectionKeyOrderByUploadedAt(sheetId, key)
            .mapNotNull { image ->
                runCatching { image.toInfo() to storage.load(image.storageKey) }.getOrNull()
            }

    /** Best-effort file cleanup for the case-purge listener, after the rows are deleted. */
    fun deleteFiles(storageKeys: List<String>) {
        storageKeys.forEach { runCatching { storage.delete(it) } }
    }

    private fun list(
        sheetId: UUID,
        key: FaSectionKey,
    ): List<FaSectionImageInfo> = images.findByAnalysisSheetIdAndSectionKeyOrderByUploadedAt(sheetId, key).map { it.toInfo() }

    private fun requireSheet(creditCaseId: UUID): AnalysisSheet =
        sheets.findByCreditCaseId(creditCaseId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucune fiche d'analyse pour ce dossier")

    private fun requireDraft(creditCaseId: UUID): AnalysisSheet {
        val sheet = requireSheet(creditCaseId)
        if (sheet.status != AnalysisSheetStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "La fiche d'analyse est déjà publiée")
        }
        return sheet
    }

    private fun requireImageSection(
        sheet: AnalysisSheet,
        key: FaSectionKey,
    ) {
        if (key.type != FaSectionType.IMAGE) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La section « ${key.label} » n'accepte pas d'images")
        }
        if (key !in FaSectionRegistry.sectionsFor(sheet.faVariant)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "La section « ${key.label} » ne s'applique pas à ce type de dossier")
        }
    }

    private fun requireImage(
        sheet: AnalysisSheet,
        key: FaSectionKey,
        imageId: UUID,
    ): AnalysisSheetImage {
        val image = images.findById(imageId).orElse(null)
        if (image == null || image.analysisSheetId != sheet.id || image.sectionKey != key) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Image introuvable")
        }
        return image
    }
}

internal fun AnalysisSheetImage.toInfo(): FaSectionImageInfo =
    FaSectionImageInfo(
        id = requireNotNull(id),
        fileName = fileName,
        contentType = contentType,
        sizeBytes = sizeBytes,
        caption = caption,
        uploadedAt = uploadedAt,
    )
