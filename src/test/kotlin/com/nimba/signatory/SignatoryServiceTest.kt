package com.nimba.signatory

import com.nimba.TestcontainersConfiguration
import com.nimba.identity.Civility
import com.nimba.identity.Department
import com.nimba.identity.internal.AnalystUserDetails
import com.nimba.identity.internal.ProfileService
import com.nimba.identity.internal.UpdateProfileRequest
import com.nimba.identity.internal.User
import com.nimba.identity.internal.UserRepository
import com.nimba.seedMember
import com.nimba.signatory.internal.SignatoryAuthorizationDto
import com.nimba.signatory.internal.SignatoryService
import com.nimba.signatory.internal.SignatorySource
import com.nimba.signatory.internal.SignatoryWriteRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class SignatoryServiceTest(
    @Autowired private val signatories: SignatoryService,
    @Autowired private val profile: ProfileService,
    @Autowired private val users: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
) {
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

    @Test
    fun `a standalone signatory carries the civility it was created with`() {
        val admin = seedMember(users, passwordEncoder, "signatory-admin-${UUID.randomUUID()}@banque.test", Department.DCM)
        authenticateAs(admin)

        val created =
            signatories.create(
                SignatoryWriteRequest(
                    nom = "Marie Camara",
                    titre = "Directrice des Engagements",
                    civility = Civility.MADAME,
                    category = SignatoryCategory.INTERNE,
                    creationReason = "Test",
                    authorizations = emptyList<SignatoryAuthorizationDto>(),
                ),
            )
        assertEquals(Civility.MADAME, created.civility)

        val updated =
            signatories.update(
                created.id,
                SignatoryWriteRequest(
                    nom = created.nom,
                    titre = created.titre,
                    civility = Civility.MONSIEUR,
                    category = created.category,
                    creationReason = created.creationReason,
                ),
            )
        assertEquals(Civility.MONSIEUR, updated.civility)
    }

    @Test
    fun `a profile signatory's civility is resolved live from the picker options`() {
        val user =
            seedMember(users, passwordEncoder, "signatory-profile-${UUID.randomUUID()}@banque.test", Department.DRI)
        authenticateAs(user)
        profile.updateName(UpdateProfileRequest("Nom Complet", titre = "Analyste", civility = Civility.MADAME, signatoryOptIn = true))

        val option =
            signatories.options().single { it.source == SignatorySource.PROFILE && it.refId == requireNotNull(user.id).toString() }
        assertEquals(Civility.MADAME, option.civility)
    }
}
