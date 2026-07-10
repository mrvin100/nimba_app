package com.nimba.creditcase

/**
 * Everything downstream that depends on a case's (productType, contractType): the
 * documents required to constitute it, which TA format and FA layout apply, and
 * whether traités are generated from its trades. One entry per valid combination —
 * see [CaseTypePolicies].
 */
data class CaseTypePolicy(
    val productType: ProductType,
    val contractType: ContractType?,
    val label: String,
    val requiredDocuments: Set<DocumentKind>,
    val scheduleFormat: ScheduleFormat,
    val faVariant: FaVariant,
    val generatesTraites: Boolean,
)

/**
 * The registry of every valid (productType, contractType) combination, in
 * declaration order (also the order the create-flow's type picker presents them).
 * Adding a product or contract type means adding one entry here, not touching
 * every consumer.
 */
object CaseTypePolicies {
    val ALL: List<CaseTypePolicy> =
        listOf(
            CaseTypePolicy(
                productType = ProductType.LEASING,
                contractType = ContractType.AVEC_CONTRAT,
                label = "Leasing — avec contrat",
                requiredDocuments = setOf(DocumentKind.TA, DocumentKind.FA),
                scheduleFormat = ScheduleFormat.LEASING,
                faVariant = FaVariant.LEASING_AVEC_CONTRAT,
                generatesTraites = true,
            ),
            CaseTypePolicy(
                productType = ProductType.LEASING,
                contractType = ContractType.SANS_CONTRAT,
                label = "Leasing — sans contrat",
                requiredDocuments = setOf(DocumentKind.TA, DocumentKind.FA),
                scheduleFormat = ScheduleFormat.LEASING,
                faVariant = FaVariant.LEASING_SANS_CONTRAT,
                generatesTraites = true,
            ),
            CaseTypePolicy(
                productType = ProductType.MC2_MUFFA,
                contractType = null,
                label = "MC2 / MUFFA",
                requiredDocuments = setOf(DocumentKind.TA, DocumentKind.FA),
                scheduleFormat = ScheduleFormat.CORE_BANKING,
                faVariant = FaVariant.MC2_MUFFA,
                generatesTraites = false,
            ),
        )

    /** Null when the combination is not one of the valid, declared types. */
    fun find(
        productType: ProductType,
        contractType: ContractType?,
    ): CaseTypePolicy? = ALL.firstOrNull { it.productType == productType && it.contractType == contractType }
}
