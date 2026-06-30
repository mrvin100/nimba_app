package com.nimba.shared.security

import com.nimba.shared.ApiProperties
import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

/**
 * Session-cookie security. Authentication is established by `POST /auth/login`,
 * which persists the [org.springframework.security.core.context.SecurityContext]
 * into an `httpOnly` session cookie (attributes configured in application.yaml:
 * `secure` in production, `SameSite=Strict`). There is no JWT — this phase is a
 * single-role internal web client, so a classic session is simpler to operate
 * correctly. Every request except login and the health probe requires an
 * authenticated session; an unauthenticated request gets a plain 401 rather than
 * a redirect, so the API never leaks a login page to a programmatic caller.
 */
@Configuration
@EnableMethodSecurity
class SecurityConfig(
    private val apiProperties: ApiProperties,
) {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

    @Bean
    fun authenticationManager(
        userDetailsService: UserDetailsService,
        passwordEncoder: PasswordEncoder,
    ): AuthenticationManager {
        val provider = DaoAuthenticationProvider(userDetailsService)
        provider.setPasswordEncoder(passwordEncoder)
        return ProviderManager(provider)
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        securityContextRepository: SecurityContextRepository,
    ): SecurityFilterChain {
        val base = apiProperties.basePath
        http
            // The session cookie is SameSite=Strict and the API is consumed only by
            // the same-origin frontend, which blunts CSRF. A full CSRF posture is
            // re-evaluated in the pre-launch security review (NIMBA-30).
            .csrf { it.disable() }
            .securityContext { it.securityContextRepository(securityContextRepository) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
            .authorizeHttpRequests {
                // Spring Security applies the filter chain to every dispatcher type.
                // Internal ERROR/FORWARD/ASYNC dispatches (e.g. the container's
                // re-dispatch to /error after a controller sends an error status)
                // must be permitted, otherwise the entry point would rewrite every
                // error response into a blank 401.
                it.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD, DispatcherType.ASYNC).permitAll()
                it.requestMatchers("$base/auth/login").permitAll()
                it.requestMatchers("/actuator/health/**").permitAll()
                // Interactive API docs (springdoc). Exposed for developer use; a
                // production deployment can disable springdoc via configuration.
                it.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                it.anyRequest().authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            // A dedicated REST endpoint (`/auth/logout`) handles logout, so the
            // default form-logout filter is turned off to keep one clear path.
            .logout { it.disable() }
        return http.build()
    }
}
