package com.nimba.signatory.internal

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SignatoryRepository : JpaRepository<Signatory, UUID>
