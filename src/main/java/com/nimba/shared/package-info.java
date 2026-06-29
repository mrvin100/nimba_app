/**
 * Shared module: genuinely cross-cutting code only (common types, errors, API
 * and security configuration). Not a dumping ground — anything specific to one
 * business module belongs in that module, behind its exposed API.
 *
 * <p>All types here are part of the module's public surface (there is no
 * {@code internal} package), so other modules may use them directly without
 * crossing a boundary.
 */
@ApplicationModule(displayName = "Shared")
package com.nimba.shared;

import org.springframework.modulith.ApplicationModule;
