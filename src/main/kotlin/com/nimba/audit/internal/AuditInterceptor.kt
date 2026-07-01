package com.nimba.audit.internal

import com.nimba.shared.AuthenticatedUser
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/**
 * Records an audit entry for every state-changing request once it completes. Reads
 * the actor from the security context (populated by then) and the correlation id from
 * the MDC. Reads (GET) and the audit endpoint itself are not audited. Recording is
 * best-effort — a failure here never fails the audited request.
 */
@Component
class AuditInterceptor(
    private val recorder: AuditRecorder,
) : HandlerInterceptor {
    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val method = request.method.uppercase()
        if (method !in MUTATING_METHODS) return
        val path = request.requestURI
        if (path.contains("/admin/audit")) return

        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal
        val actorId = (principal as? AuthenticatedUser)?.userId
        val actorEmail = authentication?.name?.takeIf { it.isNotBlank() && it != "anonymousUser" }

        try {
            recorder.record(
                actorId = actorId,
                actorEmail = actorEmail,
                action = describe(method, path),
                method = method,
                path = path,
                status = response.status,
                correlationId = MDC.get("correlationId"),
            )
        } catch (_: Exception) {
            // Never let auditing break the request it is auditing.
        }
    }

    private fun describe(
        method: String,
        path: String,
    ): String =
        when {
            path.endsWith("/auth/login") -> "Connexion"
            path.endsWith("/auth/logout") -> "Déconnexion"
            path.contains("/auth/bootstrap") -> "Initialisation de l'administrateur"
            path.contains("/auth/set-password") -> "Définition du mot de passe"
            path.contains("/auth/profile/avatar") -> "Mise à jour de la photo"
            path.contains("/auth/profile") -> "Mise à jour du profil"
            path.contains("/admin/users/import") -> "Import d'utilisateurs en masse"
            path.contains("/admin/organization") -> "Mise à jour de l'organisation"
            path.contains("/suspend") -> "Suspension d'un compte"
            path.contains("/reactivate") -> "Réactivation d'un compte"
            path.contains("/revoke") -> "Révocation d'un compte"
            path.endsWith("/memberships") -> "Modification des accès"
            path.contains("/team/members") -> "Invitation / gestion d'un membre"
            method == "POST" && path.endsWith("/admin/users") -> "Création d'un utilisateur"
            path.contains("/amortization-schedule/trades") -> "Génération de trades"
            path.contains("/amortization-schedule") -> "Import d'un échéancier"
            method == "POST" && path.endsWith("/credit-cases") -> "Création d'un dossier"
            method == "PUT" && path.contains("/credit-cases/") -> "Modification d'un dossier"
            else -> "$method $path"
        }

    private companion object {
        val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
