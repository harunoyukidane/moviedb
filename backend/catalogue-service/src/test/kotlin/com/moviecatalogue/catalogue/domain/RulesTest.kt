package com.moviecatalogue.catalogue.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class MovieRulesTest {

    private val fixedClock: Clock = Clock.fixed(LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    @Test
    fun `normalizeTitle trims and validates`() {
        assertThat(MovieRules.normalizeTitle("  Inception  ")).isEqualTo("Inception")
        assertThatThrownBy { MovieRules.normalizeTitle("  ") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { MovieRules.normalizeTitle("a".repeat(MovieRules.TITLE_MAX + 1)) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateRuntime rejects non-positive`() {
        MovieRules.validateRuntime(null)
        MovieRules.validateRuntime(120)
        assertThatThrownBy { MovieRules.validateRuntime(0) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { MovieRules.validateRuntime(-5) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `clampLimit clamps 1-100 defaulting on non-positive`() {
        assertThat(MovieRules.clampLimit(0)).isEqualTo(MovieRules.PAGE_LIMIT_DEFAULT)
        assertThat(MovieRules.clampLimit(-1)).isEqualTo(MovieRules.PAGE_LIMIT_DEFAULT)
        assertThat(MovieRules.clampLimit(101)).isEqualTo(100)
        assertThat(MovieRules.clampLimit(50)).isEqualTo(50)
    }

    @Test
    fun `clampOffset floors negatives`() {
        assertThat(MovieRules.clampOffset(-9)).isZero()
        assertThat(MovieRules.clampOffset(7)).isEqualTo(7)
    }

    @Test
    fun `validateReleaseYear allows null and bounded four-digit years, rejects out of range`() {
        MovieRules.validateReleaseYear(null)
        MovieRules.validateReleaseYear(MovieRules.RELEASE_YEAR_MIN)
        MovieRules.validateReleaseYear(MovieRules.RELEASE_YEAR_MAX)
        MovieRules.validateReleaseYear(2020)
        assertThatThrownBy { MovieRules.validateReleaseYear(MovieRules.RELEASE_YEAR_MIN - 1) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { MovieRules.validateReleaseYear(MovieRules.RELEASE_YEAR_MAX + 1) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateReleaseDate allows null and accepts the 1888-10-14 floor, rejects the day before`() {
        MovieRules.validateReleaseDate(null, fixedClock)
        MovieRules.validateReleaseDate(LocalDate.of(1888, 10, 14), fixedClock)
        assertThatThrownBy { MovieRules.validateReleaseDate(LocalDate.of(1888, 10, 13), fixedClock) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateReleaseDate accepts ten years out and rejects ten years and a day`() {
        val today = LocalDate.now(fixedClock)
        MovieRules.validateReleaseDate(today.plusYears(10), fixedClock)
        assertThatThrownBy { MovieRules.validateReleaseDate(today.plusYears(10).plusDays(1), fixedClock) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateReleaseDate reads today from the injected clock, not the system clock`() {
        val futureClock = Clock.fixed(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        // 2011-01-01 is more than 10 years after 2000-01-01, but not after the real system clock's "today".
        assertThatThrownBy { MovieRules.validateReleaseDate(LocalDate.of(2011, 1, 2), futureClock) }
            .isInstanceOf(ValidationException::class.java)
    }
}

class CreditRulesTest {

    @Test
    fun `cast requires character name`() {
        assertThatThrownBy {
            CreditRules.validateCharacterForCategory(CreditCategory.CAST, null)
        }.isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            CreditRules.validateCharacterForCategory(CreditCategory.CAST, "  ")
        }.isInstanceOf(ValidationException::class.java)
        // valid
        CreditRules.validateCharacterForCategory(CreditCategory.CAST, "Cobb")
    }

    @Test
    fun `crew forbids character name`() {
        assertThatThrownBy {
            CreditRules.validateCharacterForCategory(CreditCategory.CREW, "Nope")
        }.isInstanceOf(ValidationException::class.java)
        // valid
        CreditRules.validateCharacterForCategory(CreditCategory.CREW, null)
    }

    @Test
    fun `role category must agree with credit category`() {
        assertThatThrownBy {
            CreditRules.validateRoleCategoryAgreement(CreditCategory.CAST, CreditCategory.CREW)
        }.isInstanceOf(ValidationException::class.java)
        CreditRules.validateRoleCategoryAgreement(CreditCategory.CREW, CreditCategory.CREW)
    }

    @Test
    fun `billing order must be non-negative`() {
        CreditRules.validateBillingOrder(null)
        CreditRules.validateBillingOrder(0)
        CreditRules.validateBillingOrder(3)
        assertThatThrownBy { CreditRules.validateBillingOrder(-1) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `requireActiveCode rejects missing and inactive codes`() {
        assertThatThrownBy { CreditRules.requireActiveCode(exists = false, active = false, "genre", "X") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { CreditRules.requireActiveCode(exists = true, active = false, "role", "Y") }
            .isInstanceOf(ValidationException::class.java)
        // active + existing is fine
        CreditRules.requireActiveCode(exists = true, active = true, "genre", "HORROR")
    }

    @Test
    fun `requireExistingCode rejects only missing codes, active or inactive both pass`() {
        assertThatThrownBy { CreditRules.requireExistingCode(exists = false, "genre", "GHOST") }
            .isInstanceOf(ValidationException::class.java)
        CreditRules.requireExistingCode(exists = true, "genre", "HORROR")
    }

    @Test
    fun `normalizeCharacterName and sourceRoleName enforce bounds`() {
        assertThat(CreditRules.normalizeCharacterName("  Cobb  ")).isEqualTo("Cobb")
        assertThat(CreditRules.normalizeCharacterName("  ")).isNull()
        assertThatThrownBy { CreditRules.normalizeCharacterName("a".repeat(301)) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { CreditRules.normalizeSourceRoleName("a".repeat(151)) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `a credit is rejected when the person was not yet born at the movie's release`() {
        assertThatThrownBy {
            CreditRules.validateCreditAge("Jane Doe", LocalDate.of(2026, 4, 4), LocalDate.of(2025, 11, 20))
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `a credit for a person who was 1 at release is allowed - only unborn is rejected`() {
        // born a year before release: allowed, even though very young
        CreditRules.validateCreditAge("Jane Doe", LocalDate.of(1964, 1, 1), LocalDate.of(1965, 1, 1))
        // born on the release date itself: allowed (not after)
        CreditRules.validateCreditAge("Jane Doe", LocalDate.of(1965, 1, 1), LocalDate.of(1965, 1, 1))
    }

    @Test
    fun `validateCreditAge is a no-op when either date is absent`() {
        CreditRules.validateCreditAge("Jane Doe", null, LocalDate.of(2020, 1, 1))
        CreditRules.validateCreditAge("Jane Doe", LocalDate.of(2020, 1, 1), null)
        CreditRules.validateCreditAge("Jane Doe", null, null)
    }
}

class MovieCommentRulesTest {

    @Test
    fun `normalizeAuthorDisplayName trims and validates`() {
        assertThat(MovieCommentRules.normalizeAuthorDisplayName("  Alice  ")).isEqualTo("Alice")
        assertThatThrownBy { MovieCommentRules.normalizeAuthorDisplayName(null) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { MovieCommentRules.normalizeAuthorDisplayName("   ") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            MovieCommentRules.normalizeAuthorDisplayName("a".repeat(MovieCommentRules.AUTHOR_DISPLAY_NAME_MAX + 1))
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `normalizeText trims and validates`() {
        assertThat(MovieCommentRules.normalizeText("  Great movie!  ")).isEqualTo("Great movie!")
        assertThatThrownBy { MovieCommentRules.normalizeText(null) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { MovieCommentRules.normalizeText("   ") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy {
            MovieCommentRules.normalizeText("a".repeat(MovieCommentRules.TEXT_MAX + 1))
        }.isInstanceOf(ValidationException::class.java)
    }
}
