package com.nimba.shared.security

import com.nimba.shared.ApiProperties
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
                it.requestMatchers("$base/auth/login").permitAll()
                it.requestMatchers("/actuator/health/**").permitAll()
                it.anyRequest().authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            // A dedicated REST endpoint (`/auth/logout`) handles logout, so the
            // default form-logout filter is turned off to keep one clear path.
            .logout { it.disable() }
        return http.build()
    }
}
