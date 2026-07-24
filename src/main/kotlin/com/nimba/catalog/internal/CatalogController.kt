package com.nimba.catalog.internal

import com.nimba.catalog.LifecycleKind
import com.nimba.catalog.ProductCatalog
import com.nimba.catalog.ProductFamily
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only reference data listing every credit product and its registre, so the
 * frontend never hardcodes the product list that drives navigation and per-product
 * registres. Open to any authenticated user (it backs the chrome of every workspace).
 */
@RestController
@RequestMapping("/catalog/products")
class CatalogController {
    @GetMapping
    fun list(): List<ProductDescriptor> = ProductCatalog.ALL.map { it.toDescriptor() }
}

data class ProductDescriptor(
    /** Stable code (the [ProductFamily] name); the frontend keys registres and routes off it. */
    val family: ProductFamily,
    val label: String,
    /** Direction that pilots the product's registre. */
    val department: String,
    val lifecycle: LifecycleKind,
)

private fun ProductFamily.toDescriptor(): ProductDescriptor =
    ProductDescriptor(
        family = this,
        label = label,
        department = department,
        lifecycle = lifecycle,
    )
