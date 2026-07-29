package com.nimba.signatory.internal

import com.nimba.identity.IdentityModuleApi
import com.nimba.shared.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class SignatoryService(
    private val signatories: SignatoryRepository,
    private val identity: IdentityModuleApi,
    private val roleHierarchy: RoleHierarchy,
    private val currentUser: CurrentUser,
) {
    /** Every candidate the caller may pick: opted-in profiles (always global) plus standalone records they are authorized for. */
    @Transactional(readOnly = true)
    fun options(): List<SignatoryOptionResponse> {
        val authorities = callerAuthorities()
        val profileOptions =
            identity.signatoryEligibleUsers().map {
                SignatoryOptionResponse(SignatorySource.PROFILE, it.id.toString(), it.fullName, it.titre.orEmpty(), it.civility, null)
            }
        val standaloneOptions =
            signatories
                .findAll()
                .filter { it.isUsableBy(authorities) }
                .map {
                    SignatoryOptionResponse(SignatorySource.STANDALONE, it.id.toString(), it.nom, it.titre, it.civility, it.category)
                }
        return profileOptions + standaloneOptions
    }

    /** Every standalone signatory, unfiltered — the admin management view. */
    @Transactional(readOnly = true)
    fun list(): List<SignatoryResponse> = signatories.findAll().map { it.toResponse() }

    @Transactional
    fun create(request: SignatoryWriteRequest): SignatoryResponse {
        val signatory =
            Signatory(
                nom = request.nom,
                titre = request.titre,
                civility = request.civility,
                category = request.category,
                creationReason = request.creationReason,
                createdBy = currentUser.id(),
            )
        signatory.authorizations = request.authorizations.map { SignatoryAuthorization(it.department, it.departmentRole) }.toMutableSet()
        return signatories.save(signatory).toResponse()
    }

    @Transactional
    fun update(
        id: UUID,
        request: SignatoryWriteRequest,
    ): SignatoryResponse {
        val signatory = requireSignatory(id)
        signatory.nom = request.nom
        signatory.titre = request.titre
        signatory.civility = request.civility
        signatory.category = request.category
        signatory.creationReason = request.creationReason
        signatory.authorizations = request.authorizations.map { SignatoryAuthorization(it.department, it.departmentRole) }.toMutableSet()
        return signatory.toResponse()
    }

    @Transactional
    fun delete(id: UUID) {
        signatories.delete(requireSignatory(id))
    }

    private fun requireSignatory(id: UUID): Signatory =
        signatories.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Signataire introuvable") }

    /** The caller's own authorities, expanded through the manager>member hierarchy (a manager satisfies a member-restricted signatory). */
    private fun callerAuthorities(): Set<String> {
        val authentication = SecurityContextHolder.getContext().authentication ?: return emptySet()
        return roleHierarchy.getReachableGrantedAuthorities(authentication.authorities).mapNotNull { it.authority }.toSet()
    }
}
