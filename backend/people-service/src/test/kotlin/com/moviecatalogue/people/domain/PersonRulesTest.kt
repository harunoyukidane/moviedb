package com.moviecatalogue.people.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PersonRulesTest {

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
    fun `validateLifeDates rejects death before birth`() {
        assertThatThrownBy {
            PersonRules.validateLifeDates(LocalDate.of(2000, 1, 1), LocalDate.of(1999, 1, 1))
        }.isInstanceOf(ValidationException::class.java)
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
