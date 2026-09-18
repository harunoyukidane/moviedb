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

    @Test
    fun `normalizeSynopsis allows blank, trims, screens and bounds at 5000`() {
        assertThat(MovieRules.normalizeSynopsis("")).isEqualTo("")
        assertThat(MovieRules.normalizeSynopsis("  A story.  ")).isEqualTo("A story.")
        assertThat(MovieRules.normalizeSynopsis("a".repeat(MovieRules.SYNOPSIS_MAX))).hasSize(MovieRules.SYNOPSIS_MAX)
        assertThatThrownBy { MovieRules.normalizeSynopsis("a".repeat(MovieRules.SYNOPSIS_MAX + 1)) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `normalizeSynopsis allows newlines but rejects emoji`() {
        assertThat(MovieRules.normalizeSynopsis("Line one.\nLine two.")).isEqualTo("Line one.\nLine two.")
        assertThatThrownBy { MovieRules.normalizeSynopsis("Great movie 😀") }
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

    @Test
    fun `comment text and author accept emoji, unlike catalogue metadata`() {
        assertThat(MovieCommentRules.normalizeText("Loved it 😀")).isEqualTo("Loved it 😀")
        assertThat(MovieCommentRules.normalizeAuthorDisplayName("Al😀ice")).isEqualTo("Al😀ice")
        assertThatThrownBy { MovieRules.normalizeTitle("Title 😀") }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `a zero-width joiner between two emoji survives the comment screen, but is rejected between letters`() {
        val family = "👨‍👩‍👧" // man ZWJ woman ZWJ girl
        assertThat(MovieCommentRules.normalizeText(family)).isEqualTo(family)
        assertThatThrownBy { MovieCommentRules.normalizeText("a‍b") }
            .isInstanceOf(ValidationException::class.java)
    }
}

class TextRulesTest {

    @Test
    fun `rejects NUL, control characters, bidi overrides, and zero-width characters`() {
        assertThatThrownBy { TextRules.screen("a b", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { TextRules.screen("ab", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { TextRules.screen("a‮b", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { TextRules.screen("a​b", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `allows newlines and tabs only when the policy permits them`() {
        assertThat(TextRules.screen("a\nb\tc", "field", allowNewlines = true, allowEmoji = false)).isEqualTo("a\nb\tc")
        assertThatThrownBy { TextRules.screen("a\nb", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `NFC-normalizes so decomposed and precomposed forms are treated alike`() {
        val decomposed = "é" // e + combining acute accent
        val precomposed = "é" // é
        assertThat(TextRules.screen(decomposed, "field", allowNewlines = false, allowEmoji = false)).isEqualTo(precomposed)
    }

    @Test
    fun `rejects emoji unless the policy allows it`() {
        assertThatThrownBy { TextRules.screen("hi 😀", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
        assertThat(TextRules.screen("hi 😀", "field", allowNewlines = false, allowEmoji = true))
            .isEqualTo("hi 😀")
    }
}
