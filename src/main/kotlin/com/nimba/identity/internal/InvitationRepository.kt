package com.nimba.identity.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface InvitationRepository : JpaRepository<UserInvitation, UUID> {
    fun findByToken(token: String): UserInvitation?
}
