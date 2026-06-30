package com.nimba.shared.web

import com.nimba.shared.ApiProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.HandlerTypePredicate
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Prefixes our own controllers' mappings with the configured API base path
 * ([ApiProperties.basePath]), so each controller declares only its resource path
 * (e.g. `/auth`, `/credit-cases`) and the version lives in one place.
 *
 * The predicate matches by base package only. HandlerTypePredicate combines its
 * selectors with OR, so adding an `@RestController` selector would also match
 * third-party controllers (e.g. springdoc's `/v3/api-docs`) and wrongly prefix
 * them — breaking Swagger UI. Restricting to `com.nimba` leaves those untouched.
 */
@Configuration
class WebConfig(
    private val apiProperties: ApiProperties,
) : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.addPathPrefix(
            apiProperties.basePath,
            HandlerTypePredicate.forBasePackage("com.nimba"),
        )
    }
}
