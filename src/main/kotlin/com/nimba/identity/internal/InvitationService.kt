package com.nimba.identity.internal

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Owns the invitation lifecycle (NIMBA-35): issuing a one-time set-password token
 * for a freshly provisioned user, validating it, and consuming it when the user
 * sets their password. Tokens are opaque, single-use, and time-limited.
 */
@Service
class InvitationService(
    private val users: UserRepository,
    private val invitations: InvitationRepository,
    private val passwordEncoder: PasswordEncoder,
    private val mailer: InvitationMailer,
    private val appProperties: AppProperties,
    private val clock: Clock,
) {
    /** Issues an invitation for an already-persisted user and e-mails the link. */
    @Transactional
    fun invite(user: User) {
        val token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
        val invitation =
            UserInvitation(
                userId = requireNotNull(user.id),
                token = token,
                expiresAt = Instant.now(clock).plus(appProperties.invitationTtl),
            )
        invitations.save(invitation)
        mailer.sendInvitation(user, token)
    }

    /** Returns the invited user's identity for the set-password page, or 404/410. */
    @Transactional(readOnly = true)
    fun describe(token: String): InvitationInfoResponse {
        val invitation = usableInvitation(token)
        val user = users.findById(invitation.userId).orElseThrow { gone() }
        return InvitationInfoResponse(fullName = user.fullName, email = user.email)
    }

    /** Sets the password from a valid token and marks the invitation consumed. */
    @Transactional
    fun setPassword(request: SetPasswordRequest) {
        val invitation = usableInvitation(request.token)
        val user = users.findById(invitation.userId).orElseThrow { gone() }
        user.passwordHash = passwordEncoder.encode(request.password)
        invitation.consumedAt = Instant.now(clock)
    }

    private fun usableInvitation(token: String): UserInvitation {
        val invitation = invitations.findByToken(token) ?: throw notFound()
        if (!invitation.isUsable(Instant.now(clock))) throw gone()
        return invitation
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation introuvable")

    private fun gone() = ResponseStatusException(HttpStatus.GONE, "Cette invitation a expiré ou a déjà été utilisée")
}
