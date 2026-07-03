package com.nimba.shared

import org.springframework.data.repository.CrudRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Loads an aggregate by id or fails with a 404 carrying the given business
 * message. Centralised so every module resolves "not found" identically instead
 * of re-writing the `findById().orElseThrow { … }` block.
 */
fun <T : Any, ID : Any> CrudRepository<T, ID>.getOrThrow(
    id: ID,
    message: String,
): T = findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, message) }
