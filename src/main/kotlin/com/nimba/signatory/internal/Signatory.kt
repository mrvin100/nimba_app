package com.nimba.signatory.internal

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import com.nimba.signatory.SignatoryCategory
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A standalone signatory: someone the bank needs printed as a signer on a generated
 * document, but who has no user account (or should not have one) — e.g. an external
 * beneficiary, or an internal executive who is not part of any workflow. A user WITH
 * an account becomes pickable by opting in on their own profile instead (see
 * [com.nimba.identity.IdentityModuleApi.signatoryEligibleUsers]); that path needs no
 * row here, since name/titre are always resolved live from the account.
 */
@Entity
@Table(name = "signatory")
class Signatory(
    @Column(name = "nom", nullable = false)
    var nom: String,
    @Column(name = "titre", nullable = false)
    var titre: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    var category: SignatoryCategory,
    @Column(name = "creation_reason", nullable = false)
    var creationReason: String,
    @Column(name = "created_by", updatable = false)
    val createdBy: UUID?,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    /** No rows = usable by anyone who can pick a signatory at all; otherwise restricted. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "signatory_authorization", joinColumns = [JoinColumn(name = "signatory_id")])
    var authorizations: MutableSet<SignatoryAuthorization> = mutableSetOf()

    /** Whether a holder of any of [authorities] (already expanded through the role hierarchy) may pick this signatory. */
    fun isUsableBy(authorities: Set<String>): Boolean =
        authorizations.isEmpty() || authorizations.any { "ROLE_${it.department}_${it.departmentRole}" in authorities }
}

@Embeddable
data class SignatoryAuthorization(
    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false)
    val department: Department,
    @Enumerated(EnumType.STRING)
    @Column(name = "department_role", nullable = false)
    val departmentRole: DepartmentRole,
)
