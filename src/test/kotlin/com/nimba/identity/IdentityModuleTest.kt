package com.nimba.identity

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class IdentityModuleTest(
    @Autowired private val users: UserRepository,
    @Autowired private val identity: IdentityModuleApi,
) {
    @Test
    fun `persists a user and resolves it through the module API`() {
        val saved =
            users.saveAndFlush(
                User(
                    fullName = "Aïssatou Diallo",
                    email = "aissatou.diallo@banque.test",
                    passwordHash = "hash-not-a-real-bcrypt",
                ),
            )

        val info = identity.findUser(requireNotNull(saved.id))

        assertNotNull(info)
        assertEquals("Aïssatou Diallo", info.fullName)
        assertEquals("aissatou.diallo@banque.test", info.email)
    }

    @Test
    fun `rejects a second user with the same email`() {
        users.saveAndFlush(
            User(
                fullName = "Premier Analyste",
                email = "doublon@banque.test",
                passwordHash = "hash-a",
            ),
        )

        assertFailsWith<DataIntegrityViolationException> {
            users.saveAndFlush(
                User(
                    fullName = "Second Analyste",
                    email = "doublon@banque.test",
                    passwordHash = "hash-b",
                ),
            )
        }
    }
}
