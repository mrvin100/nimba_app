package com.nimba.identity.internal

import com.nimba.identity.Department
import com.nimba.identity.DepartmentRole
import org.apache.commons.csv.CSVFormat
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Bulk account provisioning from a CSV (NIMBA-35). Mirrors the single-account flow
 * (create without password, then invite), but many at once with a preview →
 * validate → commit cycle. A commit is all-or-nothing: if any row is invalid the
 * whole import is rejected (422) and nothing is created.
 */
@Service
class BulkUserImportService(
    private val users: UserRepository,
    private val invitations: InvitationService,
) {
    /** Parses and validates without persisting. */
    fun preview(bytes: ByteArray): BulkPreviewResponse = evaluate(parse(bytes))

    /** Validates then creates all rows (each invited), or rejects the whole file. */
    @Transactional
    fun import(bytes: ByteArray): BulkImportResponse {
        val preview = evaluate(parse(bytes))
        if (!preview.valid) throw BulkImportValidationException(preview)

        preview.rows.forEach { row ->
            val user = User(fullName = row.fullName, email = row.email)
            user.platformAdmin = row.admin
            if (row.department != null && row.role != null) {
                user.assign(Department.valueOf(row.department), DepartmentRole.valueOf(row.role))
            }
            invitations.invite(users.saveAndFlush(user))
        }
        return BulkImportResponse(created = preview.rows.size)
    }

    /** Header-validated raw rows (typed department/role); throws 400 on a bad file. */
    private fun parse(bytes: ByteArray): List<RawRow> {
        val reader = BufferedReader(InputStreamReader(ByteArrayInputStream(bytes), StandardCharsets.UTF_8))
        val format =
            CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
        return reader.use {
            val csv =
                try {
                    format.parse(it)
                } catch (ex: Exception) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier CSV n'a pas pu être lu.", ex)
                }
            csv.use { parsed ->
                val missing = REQUIRED_HEADERS - parsed.headerNames.toSet()
                if (missing.isNotEmpty()) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "En-tête invalide : colonnes manquantes : ${missing.joinToString(", ")}",
                    )
                }
                val rows =
                    parsed.map { record ->
                        RawRow(
                            lineNumber = record.recordNumber,
                            fullName = record.get("fullName").orEmpty(),
                            email = record.get("email").orEmpty(),
                            department = record.get("department").orEmpty(),
                            role = record.get("role").orEmpty(),
                            admin = record.get("admin").orEmpty(),
                        )
                    }
                if (rows.isEmpty()) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier ne contient aucune ligne de données.")
                }
                rows
            }
        }
    }

    private fun evaluate(rows: List<RawRow>): BulkPreviewResponse {
        val duplicateEmails =
            rows
                .map { it.email.lowercase() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        val evaluated = rows.map { it.evaluate(duplicateEmails) }
        val invalid = evaluated.count { !it.valid }
        return BulkPreviewResponse(
            valid = invalid == 0,
            validCount = evaluated.size - invalid,
            invalidCount = invalid,
            rows = evaluated,
        )
    }

    private fun RawRow.evaluate(duplicateEmails: Set<String>): BulkPreviewRow {
        val errors = mutableListOf<String>()
        if (fullName.isBlank()) errors.add("Nom complet requis")
        if (email.isBlank()) {
            errors.add("Adresse e-mail requise")
        } else if (!EMAIL_REGEX.matches(email)) {
            errors.add("Adresse e-mail invalide")
        } else if (email.lowercase() in duplicateEmails) {
            errors.add("Doublon dans le fichier")
        } else if (users.existsByEmail(email)) {
            errors.add("Un compte existe déjà avec cet e-mail")
        }

        val department = department.blankToNull()?.let { raw -> Department.entries.firstOrNull { it.name == raw.uppercase() } }
        if (this.department.isNotBlank() && department == null) errors.add("Direction inconnue : ${this.department}")
        val role = role.blankToNull()?.let { raw -> DepartmentRole.entries.firstOrNull { it.name == raw.uppercase() } }
        if (this.role.isNotBlank() && role == null) errors.add("Rôle inconnu : ${this.role}")

        val hasDepartment = this.department.isNotBlank()
        val hasRole = this.role.isNotBlank()
        if (hasDepartment != hasRole) errors.add("Direction et rôle doivent être renseignés ensemble")

        val admin = admin.toBooleanFlag()
        if (admin == null) errors.add("Colonne « admin » : utilisez true ou false")

        if (errors.isEmpty() && admin == false && !(hasDepartment && hasRole)) {
            errors.add("Attribuez une direction et un rôle, ou le statut administrateur")
        }

        return BulkPreviewRow(
            lineNumber = lineNumber,
            fullName = fullName,
            email = email,
            department = department?.name,
            role = role?.name,
            admin = admin ?: false,
            valid = errors.isEmpty(),
            errors = errors,
        )
    }

    private data class RawRow(
        val lineNumber: Long,
        val fullName: String,
        val email: String,
        val department: String,
        val role: String,
        val admin: String,
    )

    private fun String.blankToNull(): String? = ifBlank { null }

    private fun String.toBooleanFlag(): Boolean? =
        when (trim().lowercase()) {
            "", "false", "0", "non", "no" -> false
            "true", "1", "oui", "yes" -> true
            else -> null
        }

    companion object {
        private val REQUIRED_HEADERS = setOf("fullName", "email", "department", "role", "admin")
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** Downloadable template: header plus illustrative rows. */
        val TEMPLATE_CSV: ByteArray =
            (
                "fullName,email,department,role,admin\n" +
                    "Jean Dupont,jean.dupont@example.com,DRI,MEMBER,false\n" +
                    "Awa Camara,awa.camara@example.com,DCM,MANAGER,false\n" +
                    "Admin Global,admin.global@example.com,,,true\n"
            ).toByteArray(StandardCharsets.UTF_8)
    }
}
