package com.nimba.shared.security

import com.nimba.shared.ApiProperties
import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl
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

    /**
     * Within each direction a MANAGER inherits MEMBER, so member-level checks pass
     * for managers without duplicating authorities. Exposed as a bean so it applies
     * to both URL and method authorization. ADMIN is intentionally separate (it
     * manages users, not a direction's business).
     */
    @Bean
    fun roleHierarchy(): RoleHierarchy =
        RoleHierarchyImpl.fromHierarchy(
            """
            ROLE_DRI_MANAGER > ROLE_DRI_MEMBER
            ROLE_DCM_MANAGER > ROLE_DCM_MEMBER
            ROLE_DRC_MANAGER > ROLE_DRC_MEMBER
            """.trimIndent(),
        )

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
                // Account provisioning entry points are public by necessity: the
                // one-time first-admin bootstrap (self-disables once an admin exists)
                // and the invitation set-password flow (the user has no credentials
                // yet). All three are guarded by their own service-side checks.
                it.requestMatchers("$base/auth/bootstrap").permitAll()
                it.requestMatchers("$base/auth/invitations/**").permitAll()
                it.requestMatchers("$base/auth/set-password").permitAll()
                // Organisation display name is public so the login screen and chrome
                // reflect the configured organisation before authentication.
                it.requestMatchers(HttpMethod.GET, "$base/auth/organization").permitAll()
                it.requestMatchers(HttpMethod.GET, "$base/auth/organization/logo").permitAll()
                it.requestMatchers("/actuator/health/**").permitAll()
                // Interactive API docs (springdoc). Exposed for developer use; a
                // production deployment can disable springdoc via configuration.
                it.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                // Admin user-management is restricted to platform administrators.
                it.requestMatchers("$base/admin/**").hasRole("ADMIN")
                // Administrative acts on a dossier (archive / restore / delete):
                // matched BEFORE the general credit-cases rule so administrators
                // reach exactly these paths and nothing else of the DRI surface.
                // Method security (@PreAuthorize on the controller) enforces the
                // same role a second time, closer to the code.
                it.requestMatchers(HttpMethod.DELETE, "$base/credit-cases/*").hasRole("ADMIN")
                it.requestMatchers("$base/credit-cases/*/archive", "$base/credit-cases/*/unarchive").hasRole("ADMIN")
                // The credit-case business surface (dossiers and their nested
                // amortization-schedule / trades endpoints) belongs to the DRI
                // direction. The role hierarchy lets a DRI manager pass this check.
                it.requestMatchers("$base/credit-cases/**").hasRole("DRI_MEMBER")
                it.anyRequest().authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            // A dedicated REST endpoint (`/auth/logout`) handles logout, so the
            // default form-logout filter is turned off to keep one clear path.
            .logout { it.disable() }
        return http.build()
    }
}
