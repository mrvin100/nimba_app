package com.nimba.creditcase.internal

import com.nimba.creditcase.CaseTypePolicies
import com.nimba.creditcase.CaseTypePolicy
import com.nimba.creditcase.ContractType
import com.nimba.creditcase.DocumentKind
import com.nimba.creditcase.FaVariant
import com.nimba.creditcase.ProductType
import com.nimba.creditcase.ScheduleFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only reference data driving the create-dossier type picker (and, later, the
 * constitution checklist): every valid (productType, contractType) combination and
 * what it implies, straight from [CaseTypePolicies] so the frontend never hardcodes
 * a type list that could drift from the backend's rules.
 */
@RestController
@RequestMapping("/credit-cases/types")
class CaseTypeController {
    @GetMapping
    fun list(): List<CaseTypeResponse> = CaseTypePolicies.ALL.map { it.toResponse() }
}

data class CaseTypeResponse(
    val productType: ProductType,
    val contractType: ContractType?,
    val label: String,
    val requiredDocuments: Set<DocumentKind>,
    val scheduleFormat: ScheduleFormat,
    val faVariant: FaVariant,
    val generatesTraites: Boolean,
)

private fun CaseTypePolicy.toResponse(): CaseTypeResponse =
    CaseTypeResponse(
        productType = productType,
        contractType = contractType,
        label = label,
        requiredDocuments = requiredDocuments,
        scheduleFormat = scheduleFormat,
        faVariant = faVariant,
        generatesTraites = generatesTraites,
    )
