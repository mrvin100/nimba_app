package com.nimba.identity.internal

import com.nimba.shared.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

/**
 * Manages the current user's avatar (NIMBA-39): upload to object storage, remove, and
 * read back for serving. Only image content types are accepted.
 */
@Service
class ProfileAvatarService(
    private val users: UserRepository,
    private val currentUser: CurrentUser,
    private val storage: AvatarStorage,
) {
    @Transactional
    fun upload(file: MultipartFile): MeResponse {
        val contentType = file.contentType.orEmpty()
        if (file.isEmpty || !contentType.startsWith("image/")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier doit être une image")
        }
        val user = current()
        user.avatarKey = storage.upload(requireNotNull(user.id), contentType, file.bytes)
        return user.toMeResponse()
    }

    @Transactional
    fun delete(): MeResponse {
        val user = current()
        user.avatarKey?.let { storage.delete(it) }
        user.avatarKey = null
        return user.toMeResponse()
    }

    @Transactional(readOnly = true)
    fun read(): AvatarObject {
        val user = current()
        val key = user.avatarKey ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun avatar")
        return storage.load(key)
    }

    private fun current(): User = users.caller(currentUser)
}
