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
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

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
    private val corsProperties: CorsProperties,
) {
    private companion object {
        // The directions that participate in a dossier's review flow; a manager passes
        // these via the role hierarchy. Used to open dossier reads and the workflow
        // surface to every reviewer while keeping constitution DRI-only.
        val REVIEW_ROLES = arrayOf("DRI_MEMBER", "DCM_MEMBER", "DRC_MEMBER", "COMITE_MEMBER")
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    /**
     * CORS for the browser frontend. Locally the frontend and API are same-origin,
     * but in staging/production they are separate services (see [CorsProperties]),
     * so the configured origins must be advertised. `allowCredentials` is true
     * because authentication rides on the session cookie, which the browser only
     * sends cross-origin when the server explicitly allows credentials.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config =
            CorsConfiguration().apply {
                allowedOrigins = corsProperties.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("Authorization", "Content-Type", "X-Request-ID")
                exposedHeaders = listOf("Location", "X-Request-ID")
                allowCredentials = true
                maxAge = 3600L
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
    }

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
            ROLE_COMITE_MANAGER > ROLE_COMITE_MEMBER
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
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        val base = apiProperties.basePath
        http
            // Advertise the configured frontend origin(s); needed once the frontend
            // is deployed as a separate service (see CorsProperties).
            .cors { it.configurationSource(corsConfigurationSource) }
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
                // Workflow surface: reviewing a dossier crosses directions, so the
                // transition and queue endpoints are open to every review direction;
                // the workflow service enforces which direction may act at each stage.
                // Matched before the DRI-only rule so a DCM/DRC/COMITE reviewer can post
                // an action on a dossier that otherwise belongs to the DRI surface.
                it.requestMatchers("$base/credit-cases/*/workflow/**", "$base/workflow/**").hasAnyRole(*REVIEW_ROLES)
                // FA review surface (comments, resolve, submit verdict) crosses
                // directions just like workflow; ReviewService enforces which
                // direction may act (and on which comments) at each stage.
                // Matched before the DRI-only rule for the same reason as workflow —
                // otherwise a DCM/DRC submit falls through to hasRole("DRI_MEMBER")
                // and gets a flat 403 before reaching the controller.
                it.requestMatchers("$base/credit-cases/*/review/**").hasAnyRole(*REVIEW_ROLES)
                // Reading a dossier and its documents is open to every review direction
                // (reviewers must see the TA, FA and timeline they are judging). Only
                // the DRI mutates the dossier's constitution.
                it.requestMatchers(HttpMethod.GET, "$base/credit-cases/**").hasAnyRole(*REVIEW_ROLES)
                // Generating and drafting the PV/FMP is a DCM act (design §2/§7); reads
                // fall through to the GET rule above, already open to every reviewer.
                // Matched before the DRI-only rule for the same reason as workflow.
                it.requestMatchers("$base/credit-cases/*/pv/**", "$base/credit-cases/*/fmp/**").hasRole("DCM_MEMBER")
                // The client registry backs DCM's tender-guarantee business (the
                // Caution module), distinct from the DRI's dossier surface entirely
                // — DCM-only, no other direction reads or writes.
                it.requestMatchers("$base/clients/**").hasRole("DCM_MEMBER")
                // Constituting the dossier (create/update, TA upload, FA edit/publish,
                // trade generation) belongs to the DRI direction. The role hierarchy
                // lets a DRI manager pass this check.
                it.requestMatchers("$base/credit-cases/**").hasRole("DRI_MEMBER")
                it.anyRequest().authenticated()
            }.exceptionHandling { it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)) }
            // A dedicated REST endpoint (`/auth/logout`) handles logout, so the
            // default form-logout filter is turned off to keep one clear path.
            .logout { it.disable() }
        return http.build()
    }
}
