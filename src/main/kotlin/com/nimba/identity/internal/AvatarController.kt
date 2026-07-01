package com.nimba.identity.internal

import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

/**
 * Current user's avatar (NIMBA-39). Authenticated (the principal identifies the
 * target); the browser reads GET as an `<img>` source with the session cookie.
 */
@RestController
@RequestMapping("/auth/profile/avatar")
class AvatarController(
    private val avatars: ProfileAvatarService,
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestParam("file") file: MultipartFile,
    ): MeResponse = avatars.upload(file)

    @DeleteMapping
    fun delete(): MeResponse = avatars.delete()

    @GetMapping
    fun get(): ResponseEntity<ByteArray> {
        val avatar = avatars.read()
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(avatar.contentType))
            .cacheControl(CacheControl.noCache())
            .body(avatar.bytes)
    }
}
