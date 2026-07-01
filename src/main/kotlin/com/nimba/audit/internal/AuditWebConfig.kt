package com.nimba.audit.internal

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** Registers the audit interceptor for all requests. */
@Configuration
class AuditWebConfig(
    private val auditInterceptor: AuditInterceptor,
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(auditInterceptor)
    }
}
