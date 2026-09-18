package com.moviecatalogue.catalogue.graphql

import com.moviecatalogue.catalogue.domain.NotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class IdsTest {

    @Test
    fun `parseId parses a well-formed UUID`() {
        val id = UUID.randomUUID()
        assertThat(parseId(id.toString(), "movie")).isEqualTo(id)
    }

    @Test
    fun `parseId reports a malformed id as NOT_FOUND, naming the kind and the offending value`() {
        assertThatThrownBy { parseId("not-a-uuid", "movie") }
            .isInstanceOf(NotFoundException::class.java)
            .hasMessageContaining("movie")
            .hasMessageContaining("not-a-uuid")
    }
}
