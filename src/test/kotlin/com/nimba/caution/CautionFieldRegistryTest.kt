package com.nimba.caution

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CautionFieldRegistryTest {
    @Test
    fun `common fields all carry the COMMON scope`() {
        assertTrue(CautionFieldRegistry.commonFields().all { it.scope == CautionFieldScope.COMMON })
    }

    @Test
    fun `type-specific fields carry the SPECIFIC scope`() {
        assertTrue(CautionFieldRegistry.specificFieldsFor(CautionDocumentType.SMS).all { it.scope == CautionFieldScope.SPECIFIC })
        assertTrue(CautionFieldRegistry.specificFieldsFor(CautionDocumentType.PRO).all { it.scope == CautionFieldScope.SPECIFIC })
    }

    @Test
    fun `effective content inherits common and lets a non-blank specific value override`() {
        val common = mapOf("beneficiaire" to "EDG SA", "montant" to "100")
        val specific = mapOf("montant" to "306000000", "lot" to "Lot 4")

        val effective = CautionFieldRegistry.effectiveContent(common, specific)

        assertEquals("EDG SA", effective["beneficiaire"]) // inherited from the dossier
        assertEquals("306000000", effective["montant"]) // overridden by the document
        assertEquals("Lot 4", effective["lot"]) // specific to the document
    }

    @Test
    fun `a blank specific value does not erase the inherited common value`() {
        val effective = CautionFieldRegistry.effectiveContent(mapOf("beneficiaire" to "EDG SA"), mapOf("beneficiaire" to ""))
        assertEquals("EDG SA", effective["beneficiaire"])
        assertNull(CautionFieldRegistry.effectiveContent(emptyMap(), mapOf("x" to ""))["x"])
    }
}
