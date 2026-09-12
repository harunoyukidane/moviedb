package com.moviecatalogue.people.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PersonSearchTest {

    @Test
    fun `escapes percent underscore and backslash`() {
        assertThat(PersonSearch.escapeLike("50%")).isEqualTo("50\\%")
        assertThat(PersonSearch.escapeLike("a_b")).isEqualTo("a\\_b")
        assertThat(PersonSearch.escapeLike("back\\slash")).isEqualTo("back\\\\slash")
    }

    @Test
    fun `leaves apostrophes and unicode untouched`() {
        // apostrophe is not a LIKE metacharacter; JPA parameter binding handles quoting
        assertThat(PersonSearch.escapeLike("O'Brien")).isEqualTo("O'Brien")
        assertThat(PersonSearch.escapeLike("Björk")).isEqualTo("Björk")
        assertThat(PersonSearch.escapeLike("北野")).isEqualTo("北野")
    }

    @Test
    fun `containsPattern wraps escaped term in wildcards`() {
        assertThat(PersonSearch.containsPattern("a%b")).isEqualTo("%a\\%b%")
        assertThat(PersonSearch.containsPattern("Nolan")).isEqualTo("%Nolan%")
    }
}
