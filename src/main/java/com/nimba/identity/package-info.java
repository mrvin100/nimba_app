/**
 * Identity module: minimal DRI analyst account and session authentication.
 *
 * <p>Declared explicitly as a Spring Modulith application module so the boundary
 * is detected even while the module is still a placeholder. Business code is
 * added by the EPIC-02 stories (NIMBA-8, NIMBA-9, NIMBA-9B); until then this
 * marker is the module's only content. Other modules may depend only on this
 * module's exposed {@code IdentityModuleApi}, never on its {@code internal}
 * package.
 */
@ApplicationModule(displayName = "Identity")
package com.nimba.identity;

import org.springframework.modulith.ApplicationModule;
