package com.nimba.catalog

/**
 * The shape of a product's dossier lifecycle — the primary axis distinguishing the
 * two dossier archetypes Nimba models (see `docs/nimba-domain-model.md` §3).
 *
 * - [FINANCEMENT] : an amortized credit (Leasing, MC2/MUFFA) — carries an
 *   amortization schedule, a Fiche d'analyse, a comité workflow and a repayment
 *   followed to closure. Backed by the `creditcase` module and its satellites.
 * - [ENGAGEMENT] : a signature engagement (Caution) — no schedule, no repayment; a
 *   bundle of generated attestations with a lightweight finalize/proroge lifecycle.
 *   Backed by the `caution` module.
 */
enum class LifecycleKind {
    FINANCEMENT,
    ENGAGEMENT,
}
