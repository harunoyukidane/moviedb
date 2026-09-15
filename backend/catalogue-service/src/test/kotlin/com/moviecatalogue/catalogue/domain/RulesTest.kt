package com.moviecatalogue.catalogue.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class MovieRulesTest {

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
