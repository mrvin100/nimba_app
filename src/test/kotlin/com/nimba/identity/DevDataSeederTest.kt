package com.nimba.identity

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.DevDataSeeder
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Verifies the seed runs when enabled and is idempotent. The seed properties are
 * enabled only for this context via @TestPropertySource, so other tests are
 * unaffected.
 */
@Import(TestcontainersConfiguration::class)
@TestPropertySource(
    properties = [
        "nimba.seed.enabled=true",
        "nimba.seed.dri-email=seed@nimba.test",
        "nimba.seed.dri-password=Seed-Password-123",
    ],
)
@SpringBootTest
class DevDataSeederTest(
    @Autowired private val users: UserRepository,
    @Autowired private val seeder: DevDataSeeder,
) {
    @Test
    fun `seeds the DRI analyst once and is idempotent`() {
        // The runner already executed on startup.
        val seeded = users.findByEmail("seed@nimba.test")
        assertNotNull(seeded)
        assertEquals("DRI_ANALYST", seeded.role.name)

        // Running again must not create a duplicate.
        seeder.run(DefaultApplicationArguments())
        assertEquals(1, users.findAll().count { it.email == "seed@nimba.test" })
    }
}
