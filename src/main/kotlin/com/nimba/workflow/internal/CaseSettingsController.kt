package com.nimba.workflow.internal

import com.nimba.creditcase.CreditCaseDocumentResetRequested
import com.nimba.creditcase.CreditCaseModuleApi
import com.nimba.creditcase.ResettableDocument
import com.nimba.creditcase.getOrThrow
import com.nimba.workflow.WorkflowStatus
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The Settings tab's critical actions (design §12.3). Lives in the workflow
 * module because the gate is the dossier's lifecycle: resets are only allowed
 * while it is BROUILLON — never mid-review or after an approval — and only to
 * a DRI manager or an administrator. The reset itself is an application event
 * each document module consumes for its own data (the same fan-out as the
 * admin deletion). The dossier deletion stays the creditcase module's
 * admin-only endpoint.
 */
@RestController
@RequestMapping("/credit-cases/{caseId}/settings")
class CaseSettingsController(
    private val workflows: CaseWorkflowRepository,
    private val creditCases: CreditCaseModuleApi,
    private val events: ApplicationEventPublisher,
) {
    @PostMapping("/reset/{document}")
    @PreAuthorize("hasRole('DRI_MANAGER') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun reset(
        @PathVariable caseId: UUID,
        @PathVariable document: ResettableDocument,
    ) {
        creditCases.getOrThrow(caseId)
        val status = workflows.findByCreditCaseId(caseId)?.status ?: WorkflowStatus.BROUILLON
        if (status != WorkflowStatus.BROUILLON) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Les actions critiques ne sont possibles que sur un dossier en brouillon",
            )
        }
        events.publishEvent(CreditCaseDocumentResetRequested(caseId, document))
    }
}
