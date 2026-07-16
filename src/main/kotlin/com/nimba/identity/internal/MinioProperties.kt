package com.nimba.identity.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * MinIO/S3 connection settings for object storage (user avatars, organisation
 * logo). Defaults target the local docker-compose MinIO. In production these come
 * from MINIO_ENDPOINT / MINIO_ACCESS_KEY / MINIO_SECRET_KEY / MINIO_BUCKET and can
 * point at any hosted S3-compatible service — the app never depends on a local
 * container.
 */
@ConfigurationProperties("nimba.minio")
data class MinioProperties(
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "nimba",
    val secretKey: String = "nimba-secret",
    val bucket: String = "nimba",
)
