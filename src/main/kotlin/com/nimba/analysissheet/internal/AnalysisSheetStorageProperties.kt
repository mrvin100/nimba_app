package com.nimba.analysissheet.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Reads the SAME `nimba.minio.bucket` key the identity module's `MinioProperties`
 * binds — a shared bucket, one small properties class per module (the module
 * boundary forbids importing identity's internal `MinioProperties` directly). The
 * `MinioClient` bean itself is a third-party type and is injected as-is.
 */
@ConfigurationProperties("nimba.minio")
data class AnalysisSheetStorageProperties(
    val bucket: String = "nimba",
)
