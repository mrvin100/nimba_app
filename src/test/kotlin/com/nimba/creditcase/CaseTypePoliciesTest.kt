package com.nimba.creditcase

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaseTypePoliciesTest {
    @Test
    fun `every declared policy has a unique product-contract combination`() {
        val keys = CaseTypePolicies.ALL.map { it.productType to it.contractType }
        assertEquals(keys.toSet().size, keys.size, "duplicate (productType, contractType) entries in the registry")
    }

    @Test
    fun `resolves a known leasing combination`() {
        val policy = CaseTypePolicies.find(ProductType.LEASING, ContractType.AVEC_CONTRAT)

        assertEquals(FaVariant.LEASING_AVEC_CONTRAT, policy?.faVariant)
        assertEquals(ScheduleFormat.LEASING, policy?.scheduleFormat)
        assertTrue(policy?.generatesTraites == true)
    }

    @Test
    fun `resolves MC2_MUFFA without a contract type`() {
        val policy = CaseTypePolicies.find(ProductType.MC2_MUFFA, null)

        assertEquals(FaVariant.MC2_MUFFA, policy?.faVariant)
        assertEquals(ScheduleFormat.CORE_BANKING, policy?.scheduleFormat)
        assertTrue(policy?.generatesTraites == false)
    }

    @Test
    fun `an undeclared combination is not resolved`() {
        assertNull(CaseTypePolicies.find(ProductType.LEASING, null))
        assertNull(CaseTypePolicies.find(ProductType.MC2_MUFFA, ContractType.AVEC_CONTRAT))
    }
}
