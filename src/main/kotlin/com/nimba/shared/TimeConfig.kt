package com.nimba.shared

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Provides a single application [Clock]. Time-dependent components (login
 * throttling, trade due-date calculation) inject this rather than calling
 * `Instant.now()` / `LocalDate.now()` directly, so their behavior is
 * deterministically testable with a fixed clock.
 */
@Configuration
class TimeConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
