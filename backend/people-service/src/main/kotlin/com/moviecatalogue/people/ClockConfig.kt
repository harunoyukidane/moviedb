package com.moviecatalogue.people

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Injected system clock (V2.2-03) so "today" in date-range rules is testable
 * with `Clock.fixed` rather than reading `LocalDate.now()` directly.
 */
@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
