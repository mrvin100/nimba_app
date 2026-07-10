package com.nimba.notification

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.Department
import com.nimba.identity.internal.UserRepository
import com.nimba.notification.internal.NotificationService
import com.nimba.seedMember
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class NotificationModuleTest(
    @Autowired private val notifications: NotificationService,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    private fun memberId(
        email: String,
        department: Department,
    ): UUID = requireNotNull(seedMember(users, passwordEncoder, email, department).id)

    @Test
    fun `notifying a user creates an unread notification they can read`() {
        val recipient = memberId("notif-user@banque.test", Department.DRI)
        val caseId = UUID.randomUUID()

        notifications.notifyUser(recipient, caseId, "Dossier prêt")

        val page = notifications.list(recipient, PageRequest.of(0, 20))
        assertEquals(1, page.totalElements)
        assertEquals("Dossier prêt", page.content.first().message)
        assertEquals(caseId, page.content.first().creditCaseId)
        assertEquals(1L, notifications.unreadCount(recipient))
    }

    @Test
    fun `notifying a department fans out to every current member`() {
        val a = memberId("notif-drc-a@banque.test", Department.DRC)
        val b = memberId("notif-drc-b@banque.test", Department.DRC)
        val outsider = memberId("notif-dcm@banque.test", Department.DCM)

        notifications.notifyDepartment(Department.DRC, null, "Revue requise")

        assertEquals(1L, notifications.unreadCount(a))
        assertEquals(1L, notifications.unreadCount(b))
        assertEquals(0L, notifications.unreadCount(outsider))
    }

    @Test
    fun `marking read updates unread count and cannot be done by another recipient`() {
        val recipient = memberId("notif-read@banque.test", Department.DRI)
        val other = memberId("notif-other@banque.test", Department.DCM)
        notifications.notifyUser(recipient, null, "À lire")
        val id =
            notifications
                .list(recipient, PageRequest.of(0, 20))
                .content
                .first()
                .id

        assertFailsWith<ResponseStatusException> { notifications.markRead(id, other) }

        val marked = notifications.markRead(id, recipient)
        assertTrue(marked.read)
        assertEquals(0L, notifications.unreadCount(recipient))
    }

    @Test
    fun `marking all read clears every unread notification of the recipient`() {
        val recipient = memberId("notif-all@banque.test", Department.DRI)
        notifications.notifyUser(recipient, null, "Un")
        notifications.notifyUser(recipient, null, "Deux")

        notifications.markAllRead(recipient)

        assertEquals(0L, notifications.unreadCount(recipient))
    }
}
