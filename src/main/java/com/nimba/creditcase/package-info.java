/**
 * Credit-case module: the minimal client credit file an amortization schedule
 * and its trades attach to.
 *
 * <p>Declared explicitly as a Spring Modulith application module so the boundary
 * is detected even while the module is still a placeholder. Business code is
 * added by the EPIC-03 stories (NIMBA-10, NIMBA-11, NIMBA-12). Other modules may
 * depend only on this module's exposed {@code CreditCaseModuleApi}, never on its
 * {@code internal} package.
 */
@ApplicationModule(displayName = "Credit Case")
package com.nimba.creditcase;

import org.springframework.modulith.ApplicationModule;
