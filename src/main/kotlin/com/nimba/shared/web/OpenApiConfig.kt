package com.nimba.shared.web

import com.nimba.shared.ApiProperties
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Names the springdoc-generated document "Nimba" instead of the library's default
 * "OpenAPI definition" / "v0", and derives the displayed version from
 * [ApiProperties.basePath] so it never drifts from the actual API version.
 */
@Configuration
class OpenApiConfig(
    private val apiProperties: ApiProperties,
) {
    @Bean
    fun nimbaOpenApi(): OpenAPI =
        OpenAPI().info(
            Info()
                .title("Nimba")
                .version(apiProperties.basePath.substringAfterLast('/')),
        )
}
