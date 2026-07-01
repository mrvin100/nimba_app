package com.nimba.identity.internal

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

/**
 * Stores and retrieves the organisation logo in MinIO/S3. There is a single logo for
 * the whole (mono-tenant) organisation, so it lives under one fixed key, overwritten
 * on re-upload. The content type is kept on the settings row, so reads return only the
 * bytes. The target bucket is created on demand. Mirrors [AvatarStorage] deliberately:
 * a shared object type differs (per-user vs the singleton branding asset), and keeping
 * them separate avoids coupling the two features.
 */
@Component
class OrganizationLogoStorage(
    private val minio: MinioClient,
    private val properties: MinioProperties,
) {
    fun upload(
        contentType: String,
        bytes: ByteArray,
    ): String {
        ensureBucket()
        ByteArrayInputStream(bytes).use { stream ->
            minio.putObject(
                PutObjectArgs
                    .builder()
                    .bucket(properties.bucket)
                    .`object`(KEY)
                    .stream(stream, bytes.size.toLong(), -1)
                    .contentType(contentType)
                    .build(),
            )
        }
        return KEY
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

    private fun ensureBucket() {
        val exists = minio.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket).build())
        if (!exists) {
            minio.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket).build())
        }
    }

    private companion object {
        const val KEY = "branding/organization-logo"
    }
}
