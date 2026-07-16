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
            path.endsWith("/reset-password") -> "Réinitialisation du mot de passe"
            path.contains("/suspend") -> "Suspension d'un compte"
            path.contains("/reactivate") -> "Réactivation d'un compte"
            path.contains("/revoke") -> "Révocation d'un compte"
            path.endsWith("/memberships") -> "Modification des accès"
            method == "POST" && path.endsWith("/admin/users") -> "Création d'un utilisateur"
            method == "POST" && path.endsWith("/team/members") -> "Invitation d'un membre"
            path.contains("/amortization-schedule/trades") -> "Génération de trades"
            path.contains("/amortization-schedule") -> "Import d'un échéancier"
            method == "POST" && path.endsWith("/attachments") -> "Ajout d'une pièce jointe"
            method == "DELETE" && path.contains("/attachments/") -> "Suppression d'une pièce jointe"
            method == "POST" && path.endsWith("/guarantees") -> "Ajout d'une garantie"
            method == "PUT" && path.contains("/guarantees/") -> "Modification d'une garantie"
            method == "DELETE" && path.contains("/guarantees/") -> "Suppression d'une garantie"
            path.endsWith("/analysis-sheet/publish") -> "Publication de la fiche d'analyse"
            path.contains("/analysis-sheet/sections/") -> "Modification de la fiche d'analyse"
            method == "POST" && path.endsWith("/analysis-sheet") -> "Création de la fiche d'analyse"
            method == "POST" && path.endsWith("/fmp") -> "Génération de la fiche de mise en place"
            path.endsWith("/pv/finalize") -> "Finalisation du PV"
            method == "PUT" && path.contains("/pv") -> "Modification du PV"
            method == "POST" && path.endsWith("/pv") -> "Création du PV"
            path.contains("/workflow/actions") -> "Changement d'état du dossier"
            path.endsWith("/notifications/read-all") -> "Marquage de toutes les notifications comme lues"
            path.contains("/notifications/") && path.endsWith("/read") -> "Marquage d'une notification comme lue"
            method == "POST" && path.endsWith("/credit-cases") -> "Création d'un dossier"
            path.endsWith("/unarchive") -> "Restauration d'un dossier"
            path.endsWith("/archive") -> "Archivage d'un dossier"
            method == "DELETE" && path.contains("/credit-cases/") -> "Suppression d'un dossier"
            method == "PUT" && path.contains("/credit-cases/") -> "Modification d'un dossier"
            else -> "$method $path"
        }

    private companion object {
        val MUTATING_METHODS = setOf("POST", "PUT", "PATCH", "DELETE")
    }
}
