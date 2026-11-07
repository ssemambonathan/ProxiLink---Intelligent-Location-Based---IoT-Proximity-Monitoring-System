package com.proxilink.backend.admin

import org.springframework.data.jpa.repository.JpaRepository

interface SystemSettingsRepository : JpaRepository<SystemSettings, String>
