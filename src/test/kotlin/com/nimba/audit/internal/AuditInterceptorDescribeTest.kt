package com.nimba.audit.internal

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Every mutating endpoint must resolve to a human action label, never the raw
 * method/path (that pair is already shown separately as the "requête" column).
 * Several cases here pin down ordering: a more specific branch must be checked
 * before a broader one whose path substring it also happens to contain (e.g.
 * a review-comment path is nested under /credit-cases/{id}/, so the generic
 * credit-case DELETE branch must not shadow it).
 */
class AuditInterceptorDescribeTest {
    @Test
    fun `resolves a human label for every module's mutating endpoints`() {
        val cases =
            listOf(
                Triple("POST", "/api/v1/cautions", "Création d'un document de caution"),
                Triple("PUT", "/api/v1/cautions/1", "Modification d'un document de caution"),
                Triple("POST", "/api/v1/cautions/1/finalize", "Finalisation d'un document de caution"),
                Triple("DELETE", "/api/v1/cautions/1", "Suppression d'un document de caution"),
                Triple("POST", "/api/v1/caution-dossiers", "Création d'un dossier de caution"),
                Triple("PUT", "/api/v1/caution-dossiers/1", "Modification d'un dossier de caution"),
                Triple("POST", "/api/v1/caution-dossiers/1/finalize", "Finalisation d'un dossier de caution"),
                Triple("POST", "/api/v1/caution-dossiers/1/proroge", "Prorogation d'un dossier de caution"),
                Triple("POST", "/api/v1/caution-dossiers/1/refinalize", "Nouvelle finalisation d'un dossier de caution"),
                Triple("DELETE", "/api/v1/caution-dossiers/1", "Suppression d'un dossier de caution"),
                Triple("POST", "/api/v1/clients", "Création d'un client"),
                Triple("PUT", "/api/v1/clients/1", "Modification d'un client"),
                Triple("POST", "/api/v1/credit-cases/1/review/submit", "Soumission du dossier pour revue"),
                Triple("POST", "/api/v1/credit-cases/1/review/comments", "Ajout d'un commentaire de revue"),
                Triple("POST", "/api/v1/credit-cases/1/review/comments/2/resolve", "Résolution d'un commentaire de revue"),
                Triple("POST", "/api/v1/credit-cases/1/review/comments/2/unresolve", "Réouverture d'un commentaire de revue"),
                Triple("DELETE", "/api/v1/credit-cases/1/review/comments/2", "Suppression d'un commentaire de revue"),
                Triple("POST", "/api/v1/credit-cases/1/settings/reset/FA", "Réinitialisation d'un document généré"),
                Triple("POST", "/api/v1/credit-cases/1/analysis-sheet/unpublish", "Dépublication de la fiche d'analyse"),
                Triple("POST", "/api/v1/credit-cases/1/amortization-schedule/preview", "Prévisualisation d'un échéancier"),
                Triple("POST", "/api/v1/admin/users/import/preview", "Prévisualisation d'un import d'utilisateurs"),
                Triple("POST", "/api/v1/admin/users/import", "Import d'utilisateurs en masse"),
                Triple("POST", "/api/v1/admin/signatories", "Création d'un signataire"),
                Triple("PUT", "/api/v1/admin/signatories/1", "Modification d'un signataire"),
                Triple("DELETE", "/api/v1/admin/signatories/1", "Suppression d'un signataire"),
            )
        for ((method, path, expected) in cases) {
            assertEquals(expected, describeAction(method, path), "for $method $path")
        }
    }

    @Test
    fun `never falls back to the raw method and path for a known module route`() {
        val known =
            listOf(
                "POST" to "/api/v1/credit-cases/1/review/comments",
                "DELETE" to "/api/v1/credit-cases/1/review/comments/2",
                "DELETE" to "/api/v1/cautions/1",
                "DELETE" to "/api/v1/caution-dossiers/1",
            )
        for ((method, path) in known) {
            assertNotEquals("$method $path", describeAction(method, path))
        }
    }
}
