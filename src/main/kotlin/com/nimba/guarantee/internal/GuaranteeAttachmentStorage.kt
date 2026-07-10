package com.nimba.guarantee.internal

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.UUID

/**
 * Stores and retrieves guarantee proof files in MinIO/S3. One object per attachment
 * under `guarantees/{guaranteeId}/{attachmentId}-{fileName}`; the target bucket
 * (shared with avatars/logos) is created on demand.
 */
@Component
class GuaranteeAttachmentStorage(
    private val minio: MinioClient,
    private val properties: GuaranteeStorageProperties,
) {
    fun upload(
        guaranteeId: UUID,
        attachmentId: UUID,
        fileName: String,
        contentType: String,
        bytes: ByteArray,
    ): String {
        ensureBucket()
        val key = keyFor(guaranteeId, attachmentId, fileName)
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

    fun load(key: String): ByteArray =
        minio
            .getObject(
                GetObjectArgs
                    .builder()
                    .bucket(properties.bucket)
                    .`object`(key)
                    .build(),
            ).use { it.readAllBytes() }

    fun delete(key: String) {
        minio.removeObject(
            RemoveObjectArgs
                .builder()
                .bucket(properties.bucket)
                .`object`(key)
                .build(),
        )
    }

    private fun keyFor(
        guaranteeId: UUID,
        attachmentId: UUID,
        fileName: String,
    ) = "guarantees/$guaranteeId/$attachmentId-${fileName.sanitize()}"

    // Object keys must not carry path separators or other characters a client could
    // use to escape the guarantee's own storage prefix.
    private fun String.sanitize(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun ensureBucket() {
        val exists = minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket).build())
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket).build())
        }
    }
}
