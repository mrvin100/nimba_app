package com.nimba.audit.internal

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditEventRepository : JpaRepository<AuditEvent, UUID> {
    fun findAllByOrderByOccurredAtDesc(pageable: Pageable): Page<AuditEvent>
}
