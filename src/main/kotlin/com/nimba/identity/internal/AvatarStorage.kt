package com.nimba.identity.internal

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.StatObjectArgs
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.UUID

/** An avatar image loaded from storage, with its content type for serving. */
data class AvatarObject(
    val bytes: ByteArray,
    val contentType: String,
)

/**
 * Stores and retrieves user avatars in MinIO/S3. One object per user (overwritten on
 * re-upload) under `avatars/{userId}`. The target bucket is created on demand.
 */
@Component
class AvatarStorage(
    private val minio: MinioClient,
    private val properties: MinioProperties,
) {
    fun upload(
        userId: UUID,
        contentType: String,
        bytes: ByteArray,
    ): String {
        ensureBucket()
        val key = keyFor(userId)
        ByteArrayInputStream(bytes).use { stream ->
            minio.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(properties.bucket)
                    .`object`(key)
                    .stream(stream, bytes.size.toLong(), -1)
                    .contentType(contentType)
                    .build(),
            )
        }
        return key
    }

    fun load(key: String): AvatarObject {
        val stat =
            minio.statObject(
                StatObjectArgs
                    .builder()
                    .bucket(properties.bucket)
                    .`object`(key)
                    .build(),
            )
        val bytes =
            minio
                .getObject(
                    GetObjectArgs
                        .builder()
                        .bucket(properties.bucket)
                        .`object`(key)
                        .build(),
                ).use { it.readAllBytes() }
        return AvatarObject(bytes, stat.contentType() ?: "application/octet-stream")
    }

    fun delete(key: String) {
        minio.removeObject(
            RemoveObjectArgs
                .builder()
                .bucket(properties.bucket)
                .`object`(key)
                .build(),
        )
    }

    private fun keyFor(userId: UUID) = "avatars/$userId"

    private fun ensureBucket() {
        val exists = minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket).build())
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket).build())
        }
    }
}
