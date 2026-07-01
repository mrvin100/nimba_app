package com.nimba.amortizationschedule.internal

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Bank-side constants printed on every lettre de change (traité). These are the same
 * for every case in a given deployment (the drawing bank, its agency, the domiciliation
 * and the place of drawing), so they live in configuration rather than per-case data.
 * The tiré (lessee) and the amounts come from the case and its trades.
 */
@ConfigurationProperties("nimba.traite")
data class TraiteProperties(
    val tireur: String = "Afriland First Bank Guinée",
    val genreActivite: String = "Banque commerciale",
    val orderBeneficiary: String = "AFRILAND FIRST BANK Guinée",
    val domiciliation: String = "Afriland First Bank Guinée / Agence KALOUM",
    val accountNumber: String = "",
    val place: String = "Kaloum",
)
