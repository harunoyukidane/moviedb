package com.moviecatalogue.people.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class PersonRulesTest {

    private val fixedClock: Clock = Clock.fixed(LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)

    @Test
    fun `normalizeName trims and accepts a valid name`() {
        assertThat(PersonRules.normalizeName("  Jane Doe  ")).isEqualTo("Jane Doe")
    }

    @Test
    fun `normalizeName rejects blank and whitespace-only`() {
        assertThatThrownBy { PersonRules.normalizeName("") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { PersonRules.normalizeName("    ") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { PersonRules.normalizeName(null) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `normalizeName rejects overlong names`() {
        val tooLong = "a".repeat(PersonRules.NAME_MAX + 1)
        assertThatThrownBy { PersonRules.normalizeName(tooLong) }
            .isInstanceOf(ValidationException::class.java)
        // exactly at the bound is accepted
        val atBound = "a".repeat(PersonRules.NAME_MAX)
        assertThat(PersonRules.normalizeName(atBound)).hasSize(PersonRules.NAME_MAX)
    }

    @Test
    fun `normalizeOptionalText returns null for blank and enforces max`() {
        assertThat(PersonRules.normalizeOptionalText("  ", 10, "x")).isNull()
        assertThat(PersonRules.normalizeOptionalText(null, 10, "x")).isNull()
        assertThat(PersonRules.normalizeOptionalText(" London ", 100, "place")).isEqualTo("London")
        assertThatThrownBy { PersonRules.normalizeOptionalText("a".repeat(11), 10, "x") }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateLifeDates rejects death before birth, naming both values`() {
        assertThatThrownBy {
            PersonRules.validateLifeDates(LocalDate.of(2000, 1, 1), LocalDate.of(1999, 1, 1))
        }.isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("1999-01-01")
            .hasMessageContaining("2000-01-01")
    }

    @Test
    fun `validateLifeDates accepts equal, after, and null combinations`() {
        // equal
        PersonRules.validateLifeDates(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 1))
        // after
        PersonRules.validateLifeDates(LocalDate.of(1950, 1, 1), LocalDate.of(2000, 1, 1))
        // nulls
        PersonRules.validateLifeDates(null, LocalDate.of(2000, 1, 1))
        PersonRules.validateLifeDates(LocalDate.of(2000, 1, 1), null)
        PersonRules.validateLifeDates(null, null)
    }

    @Test
    fun `validateLifeDates rejects an implausible lifespan over 130 years`() {
        PersonRules.validateLifeDates(LocalDate.of(1900, 1, 1), LocalDate.of(2030, 1, 1))
        assertThatThrownBy {
            PersonRules.validateLifeDates(LocalDate.of(1900, 1, 1), LocalDate.of(2031, 1, 2))
        }.isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateBirthDate rejects a future date, naming the value`() {
        assertThatThrownBy { PersonRules.validateBirthDate(LocalDate.of(2026, 4, 4), fixedClock) }
            .isInstanceOf(ValidationException::class.java)
            .hasMessageContaining("2026-04-04")
    }

    @Test
    fun `validateBirthDate rejects a date less than two years ago and accepts exactly two`() {
        val today = LocalDate.now(fixedClock)
        PersonRules.validateBirthDate(today.minusYears(2), fixedClock)
        assertThatThrownBy { PersonRules.validateBirthDate(today.minusYears(2).plusDays(1), fixedClock) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateBirthDate rejects a year before 1850`() {
        PersonRules.validateBirthDate(LocalDate.of(1850, 1, 1), fixedClock)
        assertThatThrownBy { PersonRules.validateBirthDate(LocalDate.of(1849, 12, 31), fixedClock) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateBirthDate allows null`() {
        PersonRules.validateBirthDate(null, fixedClock)
    }

    @Test
    fun `validateDeathDate rejects a future date`() {
        val today = LocalDate.now(fixedClock)
        PersonRules.validateDeathDate(today, fixedClock)
        assertThatThrownBy { PersonRules.validateDeathDate(today.plusDays(1), fixedClock) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `validateDeathDate allows null`() {
        PersonRules.validateDeathDate(null, fixedClock)
    }

    @Test
    fun `date rules read today from the injected clock, not the system clock`() {
        val pastClock = Clock.fixed(LocalDate.of(2000, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC)
        // 2000-06-01 is in the "future" relative to the fixed clock, even though it is long past for the real clock.
        assertThatThrownBy { PersonRules.validateBirthDate(LocalDate.of(2000, 6, 1), pastClock) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `clampLimit clamps into 1-100 and defaults on non-positive`() {
        assertThat(PersonRules.clampLimit(0)).isEqualTo(PersonRules.SEARCH_LIMIT_DEFAULT)
        assertThat(PersonRules.clampLimit(-5)).isEqualTo(PersonRules.SEARCH_LIMIT_DEFAULT)
        assertThat(PersonRules.clampLimit(50)).isEqualTo(50)
        assertThat(PersonRules.clampLimit(1000)).isEqualTo(PersonRules.SEARCH_LIMIT_MAX)
        assertThat(PersonRules.clampLimit(1)).isEqualTo(1)
    }

    @Test
    fun `clampOffset floors negatives to zero`() {
        assertThat(PersonRules.clampOffset(-3)).isZero()
        assertThat(PersonRules.clampOffset(0)).isZero()
        assertThat(PersonRules.clampOffset(42)).isEqualTo(42)
    }

    @Test
    fun `normalizeQuery validates length bounds`() {
        assertThatThrownBy { PersonRules.normalizeQuery("") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { PersonRules.normalizeQuery("   ") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { PersonRules.normalizeQuery("a".repeat(PersonRules.QUERY_MAX_LEN + 1)) }
            .isInstanceOf(ValidationException::class.java)
        assertThat(PersonRules.normalizeQuery("  Nolan  ")).isEqualTo("Nolan")
    }

    @Test
    fun `dedupeAndCap removes duplicates preserving order`() {
        val ids = listOf(3, 1, 3, 2, 1)
        assertThat(PersonRules.dedupeAndCap(ids)).containsExactly(3, 1, 2)
    }

    @Test
    fun `dedupeAndCap enforces the batch cap on distinct count`() {
        // 201 distinct -> rejected
        val over = (1..(PersonRules.MAX_BATCH_IDS + 1)).toList()
        assertThatThrownBy { PersonRules.dedupeAndCap(over) }
            .isInstanceOf(ValidationException::class.java)
        // 200 distinct -> accepted
        val atCap = (1..PersonRules.MAX_BATCH_IDS).toList()
        assertThat(PersonRules.dedupeAndCap(atCap)).hasSize(PersonRules.MAX_BATCH_IDS)
        // duplicates that reduce distinct below cap are accepted even if raw size exceeds it
        val manyDupes = List(PersonRules.MAX_BATCH_IDS + 50) { 7 }
        assertThat(PersonRules.dedupeAndCap(manyDupes)).containsExactly(7)
    }

    @Test
    fun `normalizeBiography allows blank, trims, screens and bounds at 5000`() {
        assertThat(PersonRules.normalizeBiography("")).isEqualTo("")
        assertThat(PersonRules.normalizeBiography("  A life.  ")).isEqualTo("A life.")
        assertThat(PersonRules.normalizeBiography("a".repeat(PersonRules.BIOGRAPHY_MAX))).hasSize(PersonRules.BIOGRAPHY_MAX)
        assertThatThrownBy { PersonRules.normalizeBiography("a".repeat(PersonRules.BIOGRAPHY_MAX + 1)) }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `normalizeBiography allows newlines but rejects emoji, unlike a comment`() {
        assertThat(PersonRules.normalizeBiography("Line one.\nLine two.")).isEqualTo("Line one.\nLine two.")
        assertThatThrownBy { PersonRules.normalizeBiography("Actor 😀") }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `normalizeName rejects emoji and control characters`() {
        assertThatThrownBy { PersonRules.normalizeName("Jane 😀") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { PersonRules.normalizeName("Jane Doe") }
            .isInstanceOf(ValidationException::class.java)
    }
}

class PersonTextRulesTest {

    @Test
    fun `rejects NUL, control characters, bidi overrides, and zero-width characters`() {
        assertThatThrownBy { TextRules.screen("a b", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { TextRules.screen("a‮b", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { TextRules.screen("a​b", "field", allowNewlines = false, allowEmoji = false) }
            .isInstanceOf(ValidationException::class.java)
    }
}

class DateHelpersTest {

    @Test
    fun `parseOptional handles null blank and valid iso`() {
        assertThat(DateHelpers.parseOptional(null)).isNull()
        assertThat(DateHelpers.parseOptional("   ")).isNull()
        assertThat(DateHelpers.parseOptional("2000-01-02")).isEqualTo(LocalDate.of(2000, 1, 2))
    }

    @Test
    fun `parseOptional rejects malformed dates`() {
        assertThatThrownBy { DateHelpers.parseOptional("01/02/2000") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { DateHelpers.parseOptional("2000-13-40") }
            .isInstanceOf(ValidationException::class.java)
        assertThatThrownBy { DateHelpers.parseOptional("not-a-date") }
            .isInstanceOf(ValidationException::class.java)
    }

    @Test
    fun `format round-trips and handles null`() {
        assertThat(DateHelpers.format(null)).isNull()
        assertThat(DateHelpers.format(LocalDate.of(1999, 12, 31))).isEqualTo("1999-12-31")
    }
}
