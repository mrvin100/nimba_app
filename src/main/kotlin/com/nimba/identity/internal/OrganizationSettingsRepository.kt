package com.nimba.identity.internal

import org.springframework.data.jpa.repository.JpaRepository

interface OrganizationSettingsRepository : JpaRepository<OrganizationSettings, Int>
