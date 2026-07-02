package com.nimba.shared

import org.springframework.data.domain.Page

/**
 * Stable pagination envelope for every paged list endpoint. Defined explicitly
 * rather than serializing Spring Data's `Page`, whose JSON shape is not a
 * guaranteed contract. Lives in the shared module's root package so every module
 * exposes the exact same envelope (sub-packages of a module are internal under
 * Spring Modulith).
 */
data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean,
)

/** Maps a Spring Data page to the API envelope, converting each element. */
fun <T : Any, R> Page<T>.toPageResponse(map: (T) -> R): PageResponse<R> =
    PageResponse(
        content = content.map(map),
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
        hasNext = hasNext(),
        hasPrevious = hasPrevious(),
    )

/** Envelope for a page whose elements are already response DTOs. */
fun <T : Any> Page<T>.toPageResponse(): PageResponse<T> = toPageResponse { it }
