package com.nimba.identity.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * MinIO/S3 connection settings for object storage (user avatars). Defaults target
 * the local docker-compose MinIO; override per environment.
 */
@ConfigurationProperties("nimba.minio")
data class MinioProperties(
    val endpoint: String = "http://localhost:9000",
    val accessKey: String = "nimba",
    val secretKey: String = "nimba-secret",
    val bucket: String = "nimba",
)
