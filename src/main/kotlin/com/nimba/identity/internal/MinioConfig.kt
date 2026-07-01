package com.nimba.identity.internal

import io.minio.MinioClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Provides the MinIO client from [MinioProperties]. No connection is made here. */
@Configuration
class MinioConfig {
    @Bean
    fun minioClient(properties: MinioProperties): MinioClient =
        MinioClient
            .builder()
            .endpoint(properties.endpoint)
            .credentials(properties.accessKey, properties.secretKey)
            .build()
}
