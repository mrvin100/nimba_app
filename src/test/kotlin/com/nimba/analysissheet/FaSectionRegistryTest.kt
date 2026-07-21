package com.nimba.analysissheet

import com.nimba.creditcase.FaVariant
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FaSectionRegistryTest {
    private val avec = FaSectionRegistry.sectionsFor(FaVariant.LEASING_AVEC_CONTRAT)
    private val sans = FaSectionRegistry.sectionsFor(FaVariant.LEASING_SANS_CONTRAT)

    @Test
    fun `both leasing variants share the cover, pilier 1 core, pilier 3 and conclusion`() {
        val shared =
            listOf(
                FaSectionKey.COVER_INFOS_DEMANDE,
                FaSectionKey.PILIER1_INFOS_GENERALES,
                FaSectionKey.PILIER1_SYNTHESE,
                FaSectionKey.PILIER2_ENCAISSEMENTS,
                FaSectionKey.PILIER3_HYPOTHESE_H1,
                FaSectionKey.PILIER4_RISQUES,
                FaSectionKey.CONCLUSION_CONDITIONS_BANQUE,
            )
        shared.forEach { key ->
            assertTrue(key in avec, "$key missing from AVEC_CONTRAT")
            assertTrue(key in sans, "$key missing from SANS_CONTRAT")
        }
    }

    @Test
    fun `pilier 2 is the contract presentation for AVEC and the market analysis for SANS`() {
        assertTrue(FaSectionKey.PILIER2_CONTRAT in avec)
        assertFalse(FaSectionKey.PILIER2_MARCHE_SECTEUR in avec)

        assertTrue(FaSectionKey.PILIER2_MARCHE_SECTEUR in sans)
        assertFalse(FaSectionKey.PILIER2_CONTRAT in sans)
    }

    @Test
    fun `only SANS_CONTRAT carries the payer synthesis and the annexes`() {
        val sansOnly =
            listOf(
                FaSectionKey.PILIER1_SYNTHESE_PAYEUR,
                FaSectionKey.ANNEXE_PAYEUR_BILAN,
                FaSectionKey.ANNEXE_PAYEUR_COMPTE_RESULTAT,
                FaSectionKey.ANNEXE_LISTE_CLIENTS,
            )
        sansOnly.forEach { key ->
            assertTrue(key in sans, "$key missing from SANS_CONTRAT")
            assertFalse(key in avec, "$key must not apply to AVEC_CONTRAT")
        }
    }

    @Test
    fun `sections follow the exported document's pilier order`() {
        listOf(avec, sans).forEach { sections ->
            val pilierOrder = sections.map { it.pilier }.distinct()
            assertEquals(pilierOrder, pilierOrder.sortedBy { it.ordinal }, "piliers out of document order")
        }
        // §1.17 sits at the end of Pilier 1, right before Pilier 2.
        val afterSynthesePayeur = sans[sans.indexOf(FaSectionKey.PILIER1_SYNTHESE_PAYEUR) + 1]
        assertEquals(FaSectionKey.PILIER2_MARCHE_SECTEUR, afterSynthesePayeur)
    }

    @Test
    fun `MC2 MUFFA has no sections until its structure is provided`() {
        assertTrue(FaSectionRegistry.sectionsFor(FaVariant.MC2_MUFFA).isEmpty())
    }
}
