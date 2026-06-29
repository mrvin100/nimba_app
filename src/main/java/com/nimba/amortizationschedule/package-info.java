/**
 * Amortization-schedule module: the core of this phase — CSV upload, preview,
 * persistence, and trade generation.
 *
 * <p>Declared explicitly as a Spring Modulith application module so the boundary
 * is detected even while the module is still a placeholder. Business code is
 * added by the EPIC-04 stories (NIMBA-13 onward). It may depend only on
 * {@code CreditCaseModuleApi} and {@code IdentityModuleApi}, never on the
 * internal types of those modules.
 */
@ApplicationModule(displayName = "Amortization Schedule")
package com.nimba.amortizationschedule;

import org.springframework.modulith.ApplicationModule;
