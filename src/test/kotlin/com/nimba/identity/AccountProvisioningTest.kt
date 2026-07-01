package com.nimba.identity

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.internal.AdminUserService
import com.nimba.identity.internal.AnalystUserDetails
import com.nimba.identity.internal.BootstrapRequest
import com.nimba.identity.internal.BootstrapService
import com.nimba.identity.internal.BulkImportValidationException
import com.nimba.identity.internal.BulkUserImportService
import com.nimba.identity.internal.CreateUserRequest
import com.nimba.identity.internal.InvitationRepository
import com.nimba.identity.internal.InvitationService
import com.nimba.identity.internal.InviteMemberRequest
import com.nimba.identity.internal.MembershipPayload
import com.nimba.identity.internal.OrganizationSettingsService
import com.nimba.identity.internal.ProfileService
import com.nimba.identity.internal.PublicOrganizationController
import com.nimba.identity.internal.SetPasswordRequest
import com.nimba.identity.internal.TeamService
import com.nimba.identity.internal.UpdateOrganizationRequest
import com.nimba.identity.internal.UpdateProfileRequest
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = ["nimba.mail.enabled=false"])
class AccountProvisioningTest(
    @Autowired private val bootstrap: BootstrapService,
    @Autowired private val invitations: InvitationService,
    @Autowired private val invitationRepository: InvitationRepository,
    @Autowired private val team: TeamService,
    @Autowired private val organization: OrganizationSettingsService,
    @Autowired private val bulkImport: BulkUserImportService,
    @Autowired private val profile: ProfileService,
    @Autowired private val publicOrganization: PublicOrganizationController,
    @Autowired private val adminUsers: AdminUserService,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
    @BeforeEach
    fun cleanSlate() {
        users.deleteAll()
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    private fun authenticateAs(user: User) {
        val principal =
            AnalystUserDetails(
                userId = requireNotNull(user.id),
                fullName = user.fullName,
                memberships = user.memberships.toSet(),
                platformAdmin = user.platformAdmin,
                status = user.status,
                email = user.email,
                passwordHash = user.passwordHash,
            )
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
    }

    private fun persist(
        email: String,
        configure: (User) -> Unit,
    ): User = users.saveAndFlush(User("Test $email", email, passwordEncoder.encode("Pass-Word")).apply(configure))

    @Test
    fun `bootstrap creates the first admin then self-disables`() {
        assertTrue(bootstrap.available())

        val admin = bootstrap.bootstrap(BootstrapRequest("Root Admin", "root@prov.test", "password123"))
        assertTrue(admin.admin)

        assertFalse(bootstrap.available())
        assertFailsWith<ResponseStatusException> {
            bootstrap.bootstrap(BootstrapRequest("Second", "second@prov.test", "password123"))
        }
    }

    @Test
    fun `admin creates a user who activates through their invitation`() {
        val created =
            adminUsers.create(
                CreateUserRequest(
                    fullName = "Invited User",
                    email = "invited@prov.test",
                    admin = false,
                    memberships = listOf(MembershipPayload(Department.DRI, DepartmentRole.MEMBER)),
                ),
            )
        assertTrue(created.pending)

        val token = requireNotNull(invitationRepository.findAll().first { it.userId == created.id }.token)
        assertEquals("invited@prov.test", invitations.describe(token).email)

        invitations.setPassword(SetPasswordRequest(token, "brand-new-pass"))
        assertFalse(requireNotNull(users.findByEmail("invited@prov.test")).pending)

        assertFailsWith<ResponseStatusException> { invitations.describe("unknown-token") }
    }

    @Test
    fun `a manager invites a member into a managed direction but not elsewhere`() {
        val manager = persist("mgr@prov.test") { it.assign(Department.DRI, DepartmentRole.MANAGER) }
        authenticateAs(manager)

        val invited = team.inviteMember(InviteMemberRequest("New Member", "newmember@prov.test", Department.DRI))
        assertTrue(invited.pending)
        assertEquals(
            "MEMBER",
            invited.memberships
                .single()
                .role.name,
        )

        // The manager sees the invited member and can change their status.
        val members = team.listMembers()
        assertTrue(members.any { it.email == "newmember@prov.test" })
        val newMemberId = requireNotNull(users.findByEmail("newmember@prov.test")?.id)
        assertEquals("SUSPENDED", team.changeMemberStatus(newMemberId, AccountStatus.SUSPENDED).status)

        // But not a member of a direction they do not manage.
        val dcmMember = persist("dcmmember@prov.test") { it.assign(Department.DCM, DepartmentRole.MEMBER) }
        assertFailsWith<ResponseStatusException> {
            team.changeMemberStatus(requireNotNull(dcmMember.id), AccountStatus.SUSPENDED)
        }

        val member = persist("plainmember@prov.test") { it.assign(Department.DRI, DepartmentRole.MEMBER) }
        authenticateAs(member)
        assertFailsWith<ResponseStatusException> {
            team.inviteMember(InviteMemberRequest("Nope", "nope@prov.test", Department.DCM))
        }
    }

    @Test
    fun `an admin cannot suspend their own account`() {
        val admin = persist("selfadmin@prov.test") { it.platformAdmin = true }
        authenticateAs(admin)
        assertFailsWith<ResponseStatusException> {
            adminUsers.changeStatus(requireNotNull(admin.id), AccountStatus.SUSPENDED)
        }
    }

    @Test
    fun `a user updates their own display name and reads the public org name`() {
        val user = persist("profile@prov.test") { it.assign(Department.DRI, DepartmentRole.MEMBER) }
        authenticateAs(user)

        val me = profile.updateName(UpdateProfileRequest("Nouveau Nom"))
        assertEquals("Nouveau Nom", me.fullName)
        assertEquals(organization.get().organizationName, publicOrganization.get().organizationName)
    }

    @Test
    fun `organization settings can be read and updated`() {
        assertEquals("Nimba", organization.get().organizationName)
        val updated = organization.update(UpdateOrganizationRequest("Acme Bank", "Acme", "no-reply@acme.test"))
        assertEquals("no-reply@acme.test", updated.senderEmail)
    }

    @Test
    fun `bulk import previews validity and commits only when fully valid`() {
        val validCsv =
            """
            fullName,email,department,role,admin
            Bulk One,bulk1@prov.test,DRI,MEMBER,false
            Bulk Admin,bulk2@prov.test,,,true
            """.trimIndent().toByteArray()

        val preview = bulkImport.preview(validCsv)
        assertTrue(preview.valid)
        assertEquals(2, preview.validCount)

        val result = bulkImport.import(validCsv)
        assertEquals(2, result.created)
        assertTrue(users.existsByEmail("bulk1@prov.test"))

        val invalidCsv =
            """
            fullName,email,department,role,admin
            No Email,,DRI,MEMBER,false
            Bad Role,bad@prov.test,DRI,CHIEF,false
            """.trimIndent().toByteArray()
        assertFalse(bulkImport.preview(invalidCsv).valid)
        assertFailsWith<BulkImportValidationException> { bulkImport.import(invalidCsv) }

        val badHeader = "name,mail\nx,y".toByteArray()
        assertFailsWith<ResponseStatusException> { bulkImport.preview(badHeader) }
    }
}
