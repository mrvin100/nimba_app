package com.nimba.catalog

/**
 * A credit product family offered by the bank — the system-wide taxonomy that spans
 * every module (unlike `creditcase.ProductType`, which only knows the financement
 * module's own products). This is the single place that enumerates what products
 * exist; adding a product means adding an entry here plus its policy in the hosting
 * module (see `docs/nimba-domain-model.md` §6).
 *
 * Each family owns exactly one **registre** (the list of its dossiers) scoped to the
 * [department] that pilots it. [department] is the direction's code (matching the
 * identity module's `Department`), kept as a plain string so this leaf module depends
 * on nothing. Variant detail (Leasing avec/sans contrat, Caution SMS/ACF…) is NOT
 * duplicated here — it stays in the hosting module's own type registry.
 */
enum class ProductFamily(
    val label: String,
    val department: String,
    val lifecycle: LifecycleKind,
) {
    LEASING("Leasing", "DRI", LifecycleKind.FINANCEMENT),
    MC2_MUFFA("MC2 / MUFFA", "DRI", LifecycleKind.FINANCEMENT),
    CAUTION("Caution", "DCM", LifecycleKind.ENGAGEMENT),
}
