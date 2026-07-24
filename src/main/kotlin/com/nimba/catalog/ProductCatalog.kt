package com.nimba.catalog

/**
 * The registry of every credit product Nimba offers — the single extension point for
 * product integration. The frontend reads it (through `GET /catalog/products`) to
 * build the per-product registres and their navigation; new products plug in by
 * adding a [ProductFamily] value, not by touching consumers.
 */
object ProductCatalog {
    /** Every product family, in declaration order. */
    val ALL: List<ProductFamily> = ProductFamily.entries

    /** The families whose dossiers follow a given lifecycle archetype. */
    fun byLifecycle(kind: LifecycleKind): List<ProductFamily> = ALL.filter { it.lifecycle == kind }

    /** The families a direction pilots (its registres); [department] is a direction code. */
    fun byDepartment(department: String): List<ProductFamily> = ALL.filter { it.department == department }
}
